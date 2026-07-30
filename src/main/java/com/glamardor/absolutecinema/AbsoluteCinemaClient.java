package com.glamardor.absolutecinema;

import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.gui.ConfigScreenFactory;
import com.glamardor.absolutecinema.render.CinemaPostProcessor;
import com.glamardor.absolutecinema.voice.SpeakerTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AbsoluteCinemaClient implements ClientModInitializer {
	public static final String KEY_CATEGORY = "key.categories.absolutecinema";

	public static KeyBinding toggleKey;
	public static KeyBinding settingsKey;
	public static KeyBinding modeKey;
	public static KeyBinding gradeKey;
	public static KeyBinding profileKey;
	public static KeyBinding tripodKey;

	@Override
	public void onInitializeClient() {
		CinemaConfig.get();

		toggleKey = register("toggle", GLFW.GLFW_KEY_F7);
		settingsKey = register("settings", GLFW.GLFW_KEY_UNKNOWN);
		modeKey = register("mode", GLFW.GLFW_KEY_UNKNOWN);
		gradeKey = register("grade", GLFW.GLFW_KEY_UNKNOWN);
		profileKey = register("profile", GLFW.GLFW_KEY_UNKNOWN);
		tripodKey = register("tripod", GLFW.GLFW_KEY_UNKNOWN);

		ClientTickEvents.END_CLIENT_TICK.register(AbsoluteCinemaClient::onTick);
		CinemaCommands.register();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			// abort rather than a polite switch-off: the fade is driven by rendered frames, and
			// none are coming until the next world loads, so a fade would still be half applied
			// on the first frame there. This also drops the director's entity references.
			CinemaManager.abort();
			SpeakerTracker.clear();
			lastHurtTime = 0;
			lastHealth = -1.0f;
		});

		// Drops the render targets and re-arms the filters, so an edited shader takes on F3+T.
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
				.registerReloadListener(new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return AbsoluteCinema.id("post_effects");
					}

					@Override
					public void reload(ResourceManager manager) {
						CinemaPostProcessor.reload();
					}
				});

		AbsoluteCinema.LOGGER.info("{} ready — voice chat {}", AbsoluteCinema.MOD_NAME,
				SpeakerTracker.isVoiceChatInstalled() ? "detected" : "not installed");
	}

	private static KeyBinding register(String name, int code) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.absolutecinema." + name, InputUtil.Type.KEYSYM, code, KEY_CATEGORY));
	}

	/** hurtTime of our own player last tick, for spotting a fresh hit. */
	private static int lastHurtTime;
	private static float lastHealth = -1.0f;

	private static void onTick(MinecraftClient client) {
		checkDamageInterrupt(client);
		while (toggleKey.wasPressed()) {
			CinemaManager.toggle();
		}
		while (modeKey.wasPressed()) {
			CinemaManager.cycleMode();
		}
		while (gradeKey.wasPressed()) {
			CinemaManager.cycleGrade();
		}
		while (profileKey.wasPressed()) {
			CinemaManager.cycleProfile();
		}
		while (tripodKey.wasPressed()) {
			if (client.player != null) {
				CameraDirector.get().placeTripod(client.player.getEyePos());
				CinemaManager.notifyTripodPlaced();
			}
		}
		while (settingsKey.wasPressed()) {
			client.setScreen(ConfigScreenFactory.create(null));
		}
	}

	/**
	 * A fresh hit while filming means the scene is over: snap back to the player's own eyes so
	 * an ambush can never catch them staring at a camera orbit.
	 */
	private static void checkDamageInterrupt(MinecraftClient client) {
		if (client.player == null) {
			lastHurtTime = 0;
			lastHealth = -1.0f;
			return;
		}
		int hurtTime = client.player.hurtTime;
		float health = client.player.getHealth();
		// hurtTime jumping up = a fresh hit (it gets set to 10 and counts down); the health
		// check backs it up in case two hits land inside one hurt animation.
		boolean freshHit = hurtTime > lastHurtTime
				|| (lastHealth >= 0.0f && health < lastHealth - 0.01f);
		lastHurtTime = hurtTime;
		lastHealth = health;

		if (freshHit && CinemaManager.isActive() && CinemaConfig.get().exitOnDamage) {
			CinemaManager.abort();
			if (CinemaConfig.get().announceToggle) {
				client.player.sendMessage(
						net.minecraft.text.Text.translatable("absolutecinema.msg.damage_interrupt"), true);
			}
		}
	}
}
