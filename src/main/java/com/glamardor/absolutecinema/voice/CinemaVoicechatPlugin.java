package com.glamardor.absolutecinema.voice;

import com.glamardor.absolutecinema.AbsoluteCinema;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.minecraft.client.MinecraftClient;

/**
 * Registered under the "voicechat" entrypoint. Fabric only calls it when Simple Voice Chat is
 * actually installed, which is why nothing else in the mod touches these classes directly.
 */
public class CinemaVoicechatPlugin implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return AbsoluteCinema.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		// Someone else's voice, tagged with the entity it came from.
		registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class,
				event -> SpeakerTracker.mark(event.getEntityId()));
		// Our own microphone, so the camera turns on us as well.
		registration.registerEvent(ClientSoundEvent.class, event -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				SpeakerTracker.mark(client.player.getUuid());
			}
		});
		AbsoluteCinema.LOGGER.info("Hooked into Simple Voice Chat for speaker focus");
	}
}
