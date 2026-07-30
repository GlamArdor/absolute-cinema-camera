package com.glamardor.absolutecinema.voice;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is talking, and how recently.
 *
 * <p>Simple Voice Chat delivers audio on its own thread, so the table is concurrent and holds
 * nothing but timestamps. When Simple Voice Chat is absent nothing ever writes here and every
 * query simply returns "nobody".
 */
public final class SpeakerTracker {
	private static final Map<UUID, Long> LAST_HEARD = new ConcurrentHashMap<>();

	/** Sticky current speaker, so two people talking at once do not make the camera flip-flop. */
	@Nullable
	private static volatile UUID held;

	private static Boolean voiceChatPresent;

	private SpeakerTracker() {
	}

	public static boolean isVoiceChatInstalled() {
		if (voiceChatPresent == null) {
			voiceChatPresent = FabricLoader.getInstance().isModLoaded("voicechat");
		}
		return voiceChatPresent;
	}

	/** Called from the Simple Voice Chat plugin whenever a packet of speech arrives. */
	public static void mark(UUID speaker) {
		LAST_HEARD.put(speaker, System.currentTimeMillis());
	}

	public static void clear() {
		LAST_HEARD.clear();
		held = null;
	}

	public static boolean isSpeaking(UUID id, float holdSeconds) {
		Long last = LAST_HEARD.get(id);
		return last != null && System.currentTimeMillis() - last <= (long) (holdSeconds * 1000.0f);
	}

	/**
	 * The person the camera should be on, or null when the room has gone quiet.
	 * The previous speaker keeps the shot until they have been silent for holdSeconds.
	 */
	@Nullable
	public static UUID getCurrentSpeaker(float holdSeconds) {
		long now = System.currentTimeMillis();
		long window = (long) (holdSeconds * 1000.0f);

		UUID current = held;
		if (current != null) {
			Long last = LAST_HEARD.get(current);
			if (last != null && now - last <= window) {
				return current;
			}
		}

		UUID freshest = null;
		long freshestTime = 0L;
		for (Map.Entry<UUID, Long> entry : LAST_HEARD.entrySet()) {
			long time = entry.getValue();
			if (now - time <= window && time > freshestTime) {
				freshestTime = time;
				freshest = entry.getKey();
			}
		}
		held = freshest;
		return freshest;
	}
}
