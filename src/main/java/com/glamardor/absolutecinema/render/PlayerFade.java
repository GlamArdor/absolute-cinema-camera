package com.glamardor.absolutecinema.render;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CinemaConfig;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Fades the people who are not in the scene but are standing in the lens.
 *
 * <p>The camera already refuses to sit inside somebody, and it recomposes when the scene has been
 * hidden for long enough. Neither covers the ordinary case on a busy server: a passer-by stops
 * half a metre to one side of the lens, blocks nothing badly enough to be worth a cut, and the
 * shot is a shoulder for as long as they stand there. Cutting to a new angle every time somebody
 * walks past would ruin far more takes than it saved, so they are simply faded out for as long as
 * they are that close.
 *
 * <p>Participants are never faded. Somebody in the scene who happens to be nearest the camera is a
 * foreground, and a foreground is a shot — cutting them out would be filming the scene worse.
 */
public final class PlayerFade {
	/**
	 * Below this the body is dropped from the frame entirely rather than drawn as a hint of one.
	 * Also what stops the very last of the fade being spent on a shape pressed against the glass.
	 */
	public static final float GONE = 0.06f;

	/**
	 * Below this the armour, held items and cape stop being drawn with the body.
	 *
	 * <p>They are drawn by feature renderers, each picking its own render layer, and a cutout layer
	 * does not blend — so they cannot be faded along with the skin and would hang in the air as a
	 * solid suit around a ghost. Taken off once the body is faint enough for the swap not to read
	 * as armour vanishing off someone standing in plain sight.
	 */
	private static final float FEATURE_CUTOFF = 0.65f;

	/**
	 * Where a body's reading is kept between the tick that measures it and the frame that draws
	 * it. Rendering is handed a render state and never the entity, and the fade is a question
	 * about the entity — so the answer travels on the state.
	 */
	public static final RenderStateDataKey<Float> OPACITY =
			RenderStateDataKey.create(() -> "absolutecinema:opacity");

	private PlayerFade() {
	}

	/** The reading taken for this body when its state was last filled in. */
	public static float opacity(EntityRenderState state) {
		return ((FabricRenderState) (Object) state).getDataOrDefault(OPACITY, 1.0f);
	}

	/** 1 for anybody the camera has no quarrel with, down to 0 for a body against the lens. */
	public static float opacity(Entity entity) {
		if (!CinemaManager.isVisible()) {
			return 1.0f;
		}
		CinemaConfig config = CinemaConfig.get();
		if (!config.fadeNearbyPlayers || !config.mode.isDirected()) {
			return 1.0f;
		}
		if (CameraDirector.get().isParticipant(entity)) {
			return 1.0f;
		}
		Camera camera = LensClearing.camera();
		if (camera == null) {
			return 1.0f;
		}

		// The nearest corner of the body, not its centre: somebody who has turned side-on with a
		// shoulder in the lens is as much in the way as somebody facing it squarely, and their
		// centre is most of a block further off.
		Vec3d nearest = nearestPoint(entity.getBoundingBox(), camera.getPos());
		float nearness = LensClearing.inTheWay(nearest, config.playerFadeDistance);
		if (nearness <= 0.0f) {
			return 1.0f;
		}
		return MathHelper.clamp(1.0f - nearness, 0.0f, 1.0f);
	}

	/** Whether this body has faded far enough to be worth no frame time at all. */
	public static boolean hidden(Entity entity) {
		return opacity(entity) < GONE;
	}

	public static boolean fading(float opacity) {
		return opacity < 1.0f;
	}

	public static boolean keepsFeatures(float opacity) {
		return opacity >= FEATURE_CUTOFF;
	}

	private static Vec3d nearestPoint(Box box, Vec3d point) {
		return new Vec3d(
				MathHelper.clamp(point.x, box.minX, box.maxX),
				MathHelper.clamp(point.y, box.minY, box.maxY),
				MathHelper.clamp(point.z, box.minZ, box.maxZ));
	}
}
