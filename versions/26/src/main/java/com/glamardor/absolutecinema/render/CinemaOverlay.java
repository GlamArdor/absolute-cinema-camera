package com.glamardor.absolutecinema.render;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CinemaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The 2D half of the effect: the black bars, and the decision to hide the HUD. */
public final class CinemaOverlay {
	private static final int BAR_COLOR = 0xFF000000;

	private CinemaOverlay() {
	}

	/** The HUD goes once the bars are most of the way in, and comes back on the way out. */
	public static boolean shouldHideHud() {
		if (!CinemaManager.isVisible()
				|| !CinemaConfig.get().hideHud
				|| CinemaManager.getTransition() <= 0.35f) {
			return false;
		}
		// F3 stays visible while filming – otherwise there is no way to read the framerate.
		Minecraft client = Minecraft.getInstance();
		return !client.getDebugOverlay().showDebugScreen();
	}

	/** Whether the chat survives the HUD being hidden. It does unless the player says otherwise. */
	public static boolean shouldKeepChat() {
		return !CinemaConfig.get().hideChat;
	}

	/** Height of one black bar right now, in scaled pixels. */
	public static int barHeight(GuiGraphicsExtractor context) {
		CinemaConfig config = CinemaConfig.get();
		if (!config.letterbox) {
			return 0;
		}
		return Mth.ceil(context.guiHeight() * config.letterboxSize
				* ease(CinemaManager.getTransition()));
	}

	public static void render(GuiGraphicsExtractor context) {
		if (!CinemaManager.isVisible()) {
			return;
		}
		CinemaConfig config = CinemaConfig.get();
		int screenWidth = context.guiWidth();
		int screenHeight = context.guiHeight();

		if (config.letterbox) {
			float eased = ease(CinemaManager.getTransition());
			int bar = Mth.ceil(screenHeight * config.letterboxSize * eased);
			if (bar > 0) {
				context.fill(0, 0, screenWidth, bar, BAR_COLOR);
				context.fill(0, screenHeight - bar, screenWidth, screenHeight, BAR_COLOR);
			}
		}

		// Drawn whether or not the bars are on: it is the only feedback left once the HUD is gone.
		drawNotice(context, screenWidth, screenHeight);
	}

	/**
	 * Mode and grade changes, in the spot the vanilla action bar would use – that one is hidden
	 * along with the rest of the HUD, so switching a preset would otherwise be silent.
	 */
	private static void drawNotice(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
		Component notice = CinemaManager.getNotice();
		if (notice == null) {
			return;
		}
		float alpha = CinemaManager.getNoticeAlpha() * CinemaManager.getTransition();
		int opacity = Mth.clamp((int) (alpha * 255.0f), 0, 255);
		if (opacity < 8) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int y = screenHeight - 68;
		int x = (screenWidth - client.font.width(notice)) / 2;
		context.text(client.font, notice, x, y, 0x00FFFFFF | (opacity << 24));
	}

	private static float ease(float t) {
		float x = Mth.clamp(t, 0.0f, 1.0f);
		// Ease-out cubic: the bars arrive quickly and settle softly, like a real matte sliding in.
		float inv = 1.0f - x;
		return 1.0f - inv * inv * inv;
	}
}
