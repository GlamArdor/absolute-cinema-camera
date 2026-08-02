package com.glamardor.absolutecinema.gui;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.glamardor.absolutecinema.render.SceneDome;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Hands out whichever settings screen the player can actually run: the Cloth Config one when
 * that mod is present, our own otherwise.
 */
public final class ConfigScreenFactory {
	private ConfigScreenFactory() {
	}

	public static boolean isClothPresent() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isModLoaded("cloth-config") || loader.isModLoaded("cloth-config2");
	}

	public static Screen create(@Nullable Screen parent) {
		if (isClothPresent()) {
			try {
				// Arms the dome and the live preview itself, since only it knows its own widgets.
				return ClothConfigScreens.build(parent);
			} catch (Throwable t) {
				// A Cloth major version bump should degrade to our own screen, not crash the game.
				AbsoluteCinema.LOGGER.warn("Cloth Config screen failed, using the built-in one", t);
			}
		}
		// The built-in screen writes straight into the config as things are dragged, so it needs
		// no preview machinery — only the dome, so the scene radius can be set by looking at it.
		Screen screen = new FallbackConfigScreen(parent);
		SceneDome.arm(screen);
		return screen;
	}
}
