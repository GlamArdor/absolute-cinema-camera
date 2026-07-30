package com.glamardor.absolutecinema;

import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CameraMode;
import com.glamardor.absolutecinema.config.CinemaConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The single source of truth for "are we filming right now".
 *
 * <p>Everything here is client-render-thread state; the only exception is the speaker table,
 * which lives in its own class because Simple Voice Chat calls us from the audio thread.
 */
public final class CinemaManager {
	private static boolean active;

	/** 0 = bars fully retracted, 1 = fully extended. Drives every fade-in of the mod. */
	private static float transition;

	private static long lastFrameNanos;
	private static float frameDelta;

	/** Our own action-bar line, drawn by the overlay because the real one is hidden with the HUD. */
	@Nullable
	private static Text notice;
	private static float noticeRemaining;
	private static final float NOTICE_SECONDS = 2.6f;
	private static final float NOTICE_FADE = 0.6f;

	private CinemaManager() {
	}

	public static boolean isActive() {
		return active;
	}

	/** True while the effects are still on screen, including the fade-out after switching off. */
	public static boolean isVisible() {
		return active || transition > 0.001f;
	}

	public static float getTransition() {
		return transition;
	}

	public static float getFrameDelta() {
		return frameDelta;
	}

	public static void setActive(boolean value) {
		if (active == value) {
			return;
		}
		active = value;
		CinemaConfig config = CinemaConfig.get();
		if (active) {
			CameraDirector.get().reset();
		}
		if (config.announceToggle) {
			notifyActionBar(active
					? Text.translatable("absolutecinema.msg.enabled", config.mode.getDisplayName())
					: Text.translatable("absolutecinema.msg.disabled"));
		}
	}

	public static void toggle() {
		setActive(!active);
	}

	/**
	 * Combat interrupt: kills the mode with no fade at all. The next frame is already pure
	 * first person — bars, filters and the directed camera all gone at once.
	 */
	public static void abort() {
		active = false;
		transition = 0.0f;
		CameraDirector.get().reset();
	}

	/** True when the mod, not the player, owns the camera at this instant. */
	public static boolean isDirecting() {
		return active && CinemaConfig.get().mode.isDirected();
	}

	/**
	 * Advances every time-based value by one rendered frame. Called once per frame from the
	 * camera mixin, before anything else reads {@link #getTransition()}.
	 */
	public static void beginFrame() {
		long now = System.nanoTime();
		if (lastFrameNanos == 0L) {
			lastFrameNanos = now;
		}
		float delta = (now - lastFrameNanos) / 1_000_000_000.0f;
		lastFrameNanos = now;
		// A long freeze (chunk load, alt-tab) must not teleport the camera.
		frameDelta = Math.min(delta, 0.1f);

		if (noticeRemaining > 0.0f) {
			noticeRemaining = Math.max(0.0f, noticeRemaining - frameDelta);
		}

		CinemaConfig config = CinemaConfig.get();
		float fade = config.letterboxFadeSeconds;
		float step = fade <= 0.001f ? 1.0f : frameDelta / fade;
		if (active) {
			transition = Math.min(1.0f, transition + step);
		} else {
			transition = Math.max(0.0f, transition - step);
		}
	}

	public static void cycleMode() {
		CinemaConfig config = CinemaConfig.get();
		config.mode = config.mode.next();
		config.save();
		CameraDirector.get().reset();
		notifyActionBar(Text.translatable("absolutecinema.msg.mode", config.mode.getDisplayName()));
	}

	public static void setMode(CameraMode mode) {
		CinemaConfig config = CinemaConfig.get();
		if (config.mode == mode) {
			return;
		}
		config.mode = mode;
		config.save();
		CameraDirector.get().reset();
	}

	/** Applies a saved scene profile and says so. Returns false when the name is unknown. */
	public static boolean applyProfile(String name) {
		CinemaConfig config = CinemaConfig.get();
		if (!config.applyProfile(name)) {
			return false;
		}
		CameraDirector.get().reset();
		notifyActionBar(Text.translatable("absolutecinema.msg.profile", name,
				config.mode.getDisplayName(), config.colorGrade.getDisplayName()));
		return true;
	}

	/** Steps to the next saved profile — the one-key version of the above. */
	public static void cycleProfile() {
		var next = CinemaConfig.get().nextProfile();
		if (next == null) {
			notifyActionBar(Text.translatable("absolutecinema.msg.no_profiles"));
			return;
		}
		applyProfile(next.name);
	}

	public static void cycleGrade() {
		CinemaConfig config = CinemaConfig.get();
		config.colorGrade = config.colorGrade.next();
		config.save();
		notifyActionBar(Text.translatable("absolutecinema.msg.grade", config.colorGrade.getDisplayName()));
	}

	/**
	 * Says something above the hotbar. While filming, the vanilla action bar is hidden along with
	 * the rest of the HUD, so the overlay draws the line itself in the same place.
	 */
	private static void notifyActionBar(Text text) {
		if (isVisible() && CinemaConfig.get().hideHud) {
			notice = text;
			noticeRemaining = NOTICE_SECONDS;
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(text, true);
		}
	}

	public static void notifyTripodPlaced() {
		notifyActionBar(Text.translatable("absolutecinema.msg.tripod_placed"));
	}

	@Nullable
	public static Text getNotice() {
		return noticeRemaining > 0.0f ? notice : null;
	}

	/** 0..1, so the line can fade out instead of blinking off. */
	public static float getNoticeAlpha() {
		if (noticeRemaining <= 0.0f) {
			return 0.0f;
		}
		return Math.min(1.0f, noticeRemaining / NOTICE_FADE);
	}
}
