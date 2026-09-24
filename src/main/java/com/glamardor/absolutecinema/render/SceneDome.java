package com.glamardor.absolutecinema.render;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.glamardor.absolutecinema.config.CinemaConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Shows what the scene radius actually covers, while it is being adjusted.
 *
 * <p>The radius is the single setting people most often set wrong, because a number in blocks says
 * nothing about a room: in a tavern, fourteen blocks quietly takes in the table upstairs and the
 * two people by the door, and the camera obediently backs off to hold a building. What it needs is
 * not a better explanation but a look at it – so while the settings are open, the shape is drawn
 * around the player in green, and dragging the slider makes it grow and shrink.
 *
 * <p>What is drawn is the actual test, not a friendly approximation of it: a sphere of the scene
 * radius, cut flat top and bottom by the height limit. Anyone inside is in the shot; anyone outside
 * is not.
 */
public final class SceneDome {
	/**
	 * Rings above and below the player, ribs around, and how round each ring is drawn.
	 *
	 * <p>Dense on purpose: this mesh is standing in for a filled surface, and from across a tavern
	 * it should read as a green volume rather than as a diagram.
	 */
	private static final int RINGS = 6;
	private static final int MERIDIANS = 28;
	private static final int SEGMENTS = 56;

	/** How far apart the repeats of a heavier ring sit, in blocks. */
	private static final double LINE_SPACING = 0.045;

	/** A bright green, fully opaque: a line has no area to spare for subtlety. */
	private static final float RED = 0.20f;
	private static final float GREEN = 1.00f;
	private static final float BLUE = 0.35f;
	private static final float LINE_ALPHA = 0.85f;

	/** The ring level with the player's feet is the one being read, so it is drawn heavier. */
	private static final float FLOOR_ALPHA = 1.00f;
	private static final int FLOOR_PASSES = 5;


	/**
	 * How long the shape stays up after the last change to it.
	 *
	 * <p>Long enough to close the menu and look around: knowing whether a radius is right means
	 * seeing who is inside it, and who is inside it is a question about the room, not about the
	 * slider. It is also why the dome appears when a value changes rather than whenever the
	 * settings happen to be open – the rest of the time it would just be in the way.
	 */
	private static final long HOLD_MILLIS = 5000L;

	/** How long the background stays clear once the settings close. */
	private static final long GRACE_MILLIS = 300L;

	/**
	 * Held up permanently under -Dabsolutecinema.dome, for looking at the shape itself.
	 *
	 * <p>Its own flag rather than the general debug one: the debug flag is about narrating what the
	 * director and the filters are doing, and hanging a green dome over every development session
	 * gets in the way of testing everything else.
	 */
	private static final boolean ALWAYS = Boolean.getBoolean("absolutecinema.dome");
	private static final boolean DEBUG = Boolean.getBoolean("absolutecinema.debug");

	/**
	 * The one screen the dome belongs to.
	 *
	 * <p>A plain "a screen is open" flag was not enough: it stayed on for the pause menu, another
	 * mod's settings, anything at all – and since the dome is what suppresses the background blur,
	 * every menu in the game lost its blur along with it. The screen is therefore held by identity.
	 */
	@Nullable
	private static Screen owner;
	private static long hideAt;

	/** The shape as it was last frame, and how long the change to it stays on screen. */
	private static boolean measured;
	private static double lastRadius;
	private static double lastCap;
	private static long showUntil;

	/** Whether the settings were open last frame, and whether anything was changed while they were. */
	private static boolean wasOpen;
	private static boolean changedWhileOpen;

	/**
	 * Live readings from the two sliders, when a screen that has them is open.
	 *
	 * <p>Cloth only writes a value into the config when the player presses save, so a dome drawn
	 * from the config would sit still while the slider moves and then jump at the end – which is
	 * the one moment the picture is no longer needed. These read the widget itself, so the shape
	 * follows the drag.
	 */
	@Nullable
	private static Supplier<Integer> previewRadius;
	@Nullable
	private static Supplier<Integer> previewHeight;

	private SceneDome() {
	}

	/**
	 * True while our own settings are the screen in front – that one keeps a clear background, so
	 * the room can be seen while it is being set up. Every other screen in the game is left alone.
	 */
	public static boolean isShowing() {
		if (ALWAYS) {
			return true;
		}
		if (owner == null) {
			return false;
		}
		if (MinecraftClient.getInstance().currentScreen == owner) {
			hideAt = System.currentTimeMillis() + GRACE_MILLIS;
			return true;
		}
		if (System.currentTimeMillis() < hideAt) {
			return true;
		}
		owner = null;
		previewRadius = null;
		previewHeight = null;
		return false;
	}

	/** Wires the dome to the sliders of a screen that has just been built. */
	public static void preview(Supplier<Integer> radius, Supplier<Integer> height) {
		previewRadius = radius;
		previewHeight = height;
	}

	public static void register() {
		// After the entities, not after the translucent terrain. Fabric hands out the shared vertex
		// buffers only up to the block outline and drops them afterwards, so anything drawn later
		// has nowhere to go – the dome silently never appeared.
		WorldRenderEvents.AFTER_ENTITIES.register(SceneDome::render);
	}

	/** Called with the settings screen that has just been built. */
	public static void arm(Screen screen) {
		owner = screen;
		hideAt = System.currentTimeMillis() + GRACE_MILLIS;
	}

	/**
	 * Whether to draw the shape this frame, and a note of the shape itself.
	 *
	 * <p>Drawn because something about it changed, not because a menu is open: sliding the radius
	 * puts it up, and it stays five seconds past the last change, which outlives closing the menu.
	 * Leaving it up the whole time the settings are open would put a green wall between the player
	 * and every other setting they came to adjust.
	 */
	private static boolean visible(MinecraftClient client, double radius, double cap) {
		long now = System.currentTimeMillis();
		boolean open = owner != null && client.currentScreen == owner;

		if (open != wasOpen) {
			// The five seconds are counted from closing the menu, not from the last drag of the
			// slider: the whole point of them is to look around the room afterwards, and a player
			// who set the radius and then read a tooltip for a while would otherwise step out to
			// nothing.
			if (!open && changedWhileOpen) {
				showUntil = now + HOLD_MILLIS;
			}
			changedWhileOpen = false;
			wasOpen = open;
		}

		if (!measured) {
			measured = true;
			lastRadius = radius;
			lastCap = cap;
		} else if (Math.abs(radius - lastRadius) > 1.0E-3 || Math.abs(cap - lastCap) > 1.0E-3) {
			lastRadius = radius;
			lastCap = cap;
			showUntil = now + HOLD_MILLIS;
			if (open) {
				changedWhileOpen = true;
			}
		}
		return ALWAYS || now < showUntil;
	}

	/** A slider's own value while it is being dragged, or the saved one when no screen is up. */
	private static double live(@Nullable Supplier<Integer> slider, float saved) {
		if (slider == null) {
			return saved;
		}
		try {
			Integer value = slider.get();
			return value == null ? saved : value;
		} catch (Throwable ignored) {
			// A screen torn down mid-frame is not worth a crash.
			return saved;
		}
	}

	private static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}

		CinemaConfig config = CinemaConfig.get();
		double radius = Math.max(0.5, live(previewRadius, config.sceneRadius));
		double limit = live(previewHeight, config.sceneHeightLimit);
		double cap = limit > 0.0 ? Math.min(limit, radius) : radius;

		if (!visible(client, radius, cap)) {
			return;
		}
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) {
			if (DEBUG) {
				AbsoluteCinema.LOGGER.warn("[dome] no buffers this frame: matrices={} consumers={}",
						matrices != null, consumers != null);
			}
			return;
		}

		Vec3d centre = client.player.getPos();
		Vec3d camera = context.camera().getPos();

		matrices.push();
		matrices.translate(centre.x - camera.x, centre.y - camera.y, centre.z - camera.z);
		MatrixStack.Entry entry = matrices.peek();
		double top = Math.asin(Math.min(1.0, cap / radius));

		/*
		 * Lines, and only lines.
		 *
		 * A tinted skin was tried twice and abandoned. As debug quads it reached the screen before
		 * the entities did, so the near wall wrote itself into the depth buffer and everybody
		 * behind it was discarded. Moved into the pass translucent parts of entities use – which
		 * should have been the right home for it – it went on failing to appear from outside under
		 * a shader pack. Chasing it further would have meant tuning against one pack's private
		 * decisions about which geometry it will carry, which is not a thing that stays fixed.
		 *
		 * A mesh dense enough reads as the same green volume, is drawn with the layer block
		 * outlines and hitboxes use – which every pack must support, since targeting blocks depends
		 * on it – and hides nobody, a line having no area to hide anything behind.
		 */
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
		// The ring at the player's own feet is the one that answers the question – who is inside –
		// so the rings are laid out from there outwards rather than from the bottom up.
		for (int ring = -RINGS; ring <= RINGS; ring++) {
			double latitude = top * ring / RINGS;
			boolean floor = ring == 0;
			band(buffer, entry, radius, latitude, floor ? FLOOR_ALPHA : LINE_ALPHA,
					floor ? FLOOR_PASSES : 1);
		}
		for (int meridian = 0; meridian < MERIDIANS; meridian++) {
			rib(buffer, entry, radius, top, Math.PI * 2.0 * meridian / MERIDIANS);
		}

		matrices.pop();
	}

	/**
	 * One horizontal ring.
	 *
	 * <p>The dome is a cage and not a surface on purpose. A filled sphere is easy to see and hides
	 * everybody inside it, which defeats the point – the question being asked is who is in the
	 * scene, and that cannot be answered through a green wall.
	 */
	private static void band(VertexConsumer buffer, MatrixStack.Entry entry, double radius,
			double latitude, float alpha, int passes) {
		double y = Math.sin(latitude) * radius;
		double ring = Math.cos(latitude) * radius;
		for (int pass = 0; pass < passes; pass++) {
			// Drawn more than once, a fraction apart, where a ring needs to read as heavier: a line
			// is a line however much you ask for, so weight has to come from repeating it.
			double offset = (pass - (passes - 1) * 0.5) * LINE_SPACING;
			for (int segment = 0; segment < SEGMENTS; segment++) {
				double from = Math.PI * 2.0 * segment / SEGMENTS;
				double to = Math.PI * 2.0 * (segment + 1) / SEGMENTS;
				line(buffer, entry,
						Math.cos(from) * ring, y + offset, Math.sin(from) * ring,
						Math.cos(to) * ring, y + offset, Math.sin(to) * ring, alpha);
			}
		}
	}

	/** One vertical rib, from the bottom cut to the top one, following the curve. */
	private static void rib(VertexConsumer buffer, MatrixStack.Entry entry, double radius,
			double limit, double angle) {
		int steps = RINGS * 4;
		for (int step = 0; step < steps; step++) {
			double lower = -limit + 2.0 * limit * step / steps;
			double upper = -limit + 2.0 * limit * (step + 1) / steps;
			line(buffer, entry,
					Math.cos(angle) * Math.cos(lower) * radius, Math.sin(lower) * radius,
					Math.sin(angle) * Math.cos(lower) * radius,
					Math.cos(angle) * Math.cos(upper) * radius, Math.sin(upper) * radius,
					Math.sin(angle) * Math.cos(upper) * radius, LINE_ALPHA);
		}
	}

	/** A single segment. The lines layer wants a direction per vertex as well as a position. */
	private static void line(VertexConsumer buffer, MatrixStack.Entry entry, double x1, double y1,
			double z1, double x2, double y2, double z2, float alpha) {
		float dx = (float) (x2 - x1);
		float dy = (float) (y2 - y1);
		float dz = (float) (z2 - z1);
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-5f) {
			return;
		}
		dx /= length;
		dy /= length;
		dz /= length;
		buffer.vertex(entry, (float) x1, (float) y1, (float) z1).color(RED, GREEN, BLUE, alpha)
				.normal(entry, dx, dy, dz);
		buffer.vertex(entry, (float) x2, (float) y2, (float) z2).color(RED, GREEN, BLUE, alpha)
				.normal(entry, dx, dy, dz);
	}

	private static void put(VertexConsumer buffer, MatrixStack.Entry entry, double x, double y, double z,
			float alpha) {
		buffer.vertex(entry, (float) x, (float) y, (float) z).color(RED, GREEN, BLUE, alpha);
	}
}
