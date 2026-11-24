package com.glamardor.absolutecinema;

import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.chat.ChatWatcher;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.gui.ConfigScreenFactory;
import com.glamardor.absolutecinema.gui.LivePreview;
import com.glamardor.absolutecinema.render.CinemaPostProcessor;
import com.glamardor.absolutecinema.render.SceneDome;
import com.glamardor.absolutecinema.voice.SpeakerTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class AbsoluteCinemaClient implements ClientModInitializer {
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(AbsoluteCinema.id("main"));

	public static KeyMapping toggleKey;
	public static KeyMapping settingsKey;
	public static KeyMapping modeKey;
	public static KeyMapping gradeKey;
	public static KeyMapping profileKey;
	public static KeyMapping tripodKey;
	public static KeyMapping shotKey;
	public static KeyMapping radiusUpKey;
	public static KeyMapping radiusDownKey;
	public static KeyMapping fadeKey;

	@Override
	public void onInitializeClient() {
		CinemaConfig.get();

		toggleKey = register("toggle", InputConstants.KEY_F7);
		settingsKey = register("settings", InputConstants.UNKNOWN.getValue());
		modeKey = register("mode", InputConstants.UNKNOWN.getValue());
		// F7 in, F8 through the looks: the two things that get pressed during a scene, next to each
		// other, and neither taken by vanilla.
		gradeKey = register("grade", InputConstants.KEY_F8);
		profileKey = register("profile", InputConstants.UNKNOWN.getValue());
		tripodKey = register("tripod", InputConstants.UNKNOWN.getValue());
		// Next to the other two, and the one an operator reaches for most once the shots are held
		// rather than timed.
		shotKey = register("shot", InputConstants.KEY_F9);
		// Unbound by default, like every other second-rank key here: these are for people who film
		// often enough to want the scene resized mid-dialogue, and a default binding of ours would
		// be one more key taken from somebody who never uses them.
		radiusUpKey = register("radius_up", InputConstants.UNKNOWN.getValue());
		radiusDownKey = register("radius_down", InputConstants.UNKNOWN.getValue());
		fadeKey = register("fade", InputConstants.UNKNOWN.getValue());

		ClientTickEvents.END_CLIENT_TICK.register(AbsoluteCinemaClient::onTick);
		CinemaCommands.register();
		ChatWatcher.register();
		SceneDome.register();

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
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
				.registerReloadListener(new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return AbsoluteCinema.id("post_effects");
					}

					@Override
					public void onResourceManagerReload(ResourceManager manager) {
						CinemaPostProcessor.reload();
					}
				});

		AbsoluteCinema.LOGGER.info("{} ready – voice chat {}", AbsoluteCinema.MOD_NAME,
				SpeakerTracker.isVoiceChatInstalled() ? "detected" : "not installed");
	}

	private static KeyMapping register(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.absolutecinema." + name, InputConstants.Type.KEYBOARD, code, KEY_CATEGORY));
	}

	/** hurtTime of our own player last tick, for spotting a fresh hit. */
	private static int lastHurtTime;
	private static float lastHealth = -1.0f;

	private static void onTick(Minecraft client) {
		LivePreview.tick(client);
		checkDamageInterrupt(client);
		while (toggleKey.consumeClick()) {
			CinemaManager.toggle();
		}
		while (modeKey.consumeClick()) {
			CinemaManager.cycleMode();
		}
		while (gradeKey.consumeClick()) {
			CinemaManager.cycleGrade();
		}
		while (profileKey.consumeClick()) {
			CinemaManager.cycleProfile();
		}
		while (tripodKey.consumeClick()) {
			if (client.player != null) {
				CameraDirector.get().placeTripod(client.player.getEyePosition());
				CinemaManager.notifyTripodPlaced();
			}
		}
		while (shotKey.consumeClick()) {
			CameraDirector.get().requestNewShot();
		}
		while (radiusUpKey.consumeClick()) {
			CinemaManager.adjustSceneRadius(1);
		}
		while (radiusDownKey.consumeClick()) {
			CinemaManager.adjustSceneRadius(-1);
		}
		while (fadeKey.consumeClick()) {
			CinemaManager.toggleFade();
		}
		while (settingsKey.consumeClick()) {
			client.gui.setScreen(ConfigScreenFactory.create(null));
		}
	}

	/**
	 * A fresh hit while filming means the scene is over: snap back to the player's own eyes so
	 * an ambush can never catch them staring at a camera orbit.
	 */
	private static void checkDamageInterrupt(Minecraft client) {
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
				client.player.sendOverlayMessage(
						net.minecraft.network.chat.Component.translatable("absolutecinema.msg.damage_interrupt"));
			}
		}
	}
}
