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

	/** When the current speaker took the frame, and who is serving out a turn-length ban. */
	private static volatile long heldSince;
	@Nullable
	private static volatile UUID resting;
	private static volatile long restingUntil;

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
		resting = null;
		restingUntil = 0L;
	}

	public static boolean isSpeaking(UUID id, float holdSeconds) {
		Long last = LAST_HEARD.get(id);
		return last != null && System.currentTimeMillis() - last <= (long) (holdSeconds * 1000.0f);
	}

	/**
	 * The person the camera should be on, or null when the room has gone quiet.
	 *
	 * <p>Two different timings, because they answer two different questions. <em>Handover</em> is
	 * how long the person holding the frame has to pause before somebody else may take it: a
	 * conversation is made of short replies, and if the camera waits for the whole hold window
	 * before letting go, every second line is missed. <em>Hold</em> is how long the last speaker
	 * keeps the frame once the room is silent, so a pause for breath does not throw the camera
	 * back to a wide shot.
	 *
	 * <p>A turn also has a maximum length. Somebody who holds the talk key down, or whose mic is
	 * open, is otherwise the only thing the camera ever sees; past maxFocusSeconds the frame goes
	 * to whoever else is speaking, or back to the room for breakSeconds if nobody is.
	 */
	@Nullable
	public static UUID getCurrentSpeaker(float holdSeconds, float handoverSeconds, float maxFocusSeconds,
			float breakSeconds) {
		long now = System.currentTimeMillis();
		long hold = (long) (holdSeconds * 1000.0f);
		long handover = (long) (handoverSeconds * 1000.0f);
		long maxFocus = (long) (maxFocusSeconds * 1000.0f);

		if (resting != null && now >= restingUntil) {
			resting = null;
		}

		UUID freshest = pickFreshest(now, handover, null);
		UUID current = held;

		UUID chosen;
		if (current != null && !current.equals(freshest)) {
			Long last = LAST_HEARD.get(current);
			long silence = last == null ? Long.MAX_VALUE : now - last;
			if (silence <= handover) {
				// Still mid-sentence: nobody interrupts them, however loudly they try.
				chosen = current;
			} else if (freshest != null) {
				// They have stopped and somebody else is talking — the frame is the other's.
				chosen = freshest;
			} else if (silence <= hold) {
				// The room has simply gone quiet; hold the last speaker a moment longer.
				chosen = current;
			} else {
				chosen = null;
			}
		} else {
			chosen = freshest;
		}

		if (chosen != null && chosen.equals(resting)) {
			// Serving out a turn: anybody else may have the frame, otherwise nobody does.
			chosen = pickFreshest(now, handover, resting);
		}

		if (chosen == null) {
			held = null;
			return null;
		}
		if (!chosen.equals(held)) {
			held = chosen;
			heldSince = now;
			return chosen;
		}
		if (maxFocus > 0L && now - heldSince > maxFocus) {
			UUID other = pickFreshest(now, handover, chosen);
			if (other != null) {
				held = other;
				heldSince = now;
				return other;
			}
			resting = chosen;
			restingUntil = now + (long) (breakSeconds * 1000.0f);
			held = null;
			return null;
		}
		return chosen;
	}

	/** The most recent voice inside the active window, skipping one uuid if asked. */
	@Nullable
	private static UUID pickFreshest(long now, long window, @Nullable UUID skip) {
		UUID best = null;
		long bestTime = 0L;
		for (Map.Entry<UUID, Long> entry : LAST_HEARD.entrySet()) {
			long time = entry.getValue();
			if (now - time > window || time <= bestTime) {
				continue;
			}
			if (skip != null && skip.equals(entry.getKey())) {
				continue;
			}
			bestTime = time;
			best = entry.getKey();
		}
		return best;
	}
}
