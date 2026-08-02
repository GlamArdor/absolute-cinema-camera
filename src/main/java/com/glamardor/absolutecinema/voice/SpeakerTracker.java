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

	/**
	 * Attention won by writing rather than by talking: who, when they wrote, and how long the
	 * frame is theirs for.
	 *
	 * <p>Kept apart from the voices on purpose. Speech is a duration — it keeps arriving, and the
	 * timestamp keeps moving, which is what the hold and handover windows are measuring. A message
	 * is an instant with a reading time attached, and it can never be allowed to outrank somebody
	 * who is actually talking: were both in one table, the newest message would always look like
	 * the freshest sound.
	 */
	private static final Map<UUID, Written> WRITTEN = new ConcurrentHashMap<>();

	private record Written(long at, long until) {
	}

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

	/** Called when somebody's chat message or roleplay emote arrives, with its reading time. */
	public static void markWritten(UUID author, float seconds) {
		long now = System.currentTimeMillis();
		WRITTEN.put(author, new Written(now, now + (long) (seconds * 1000.0f)));
	}

	public static void clear() {
		LAST_HEARD.clear();
		WRITTEN.clear();
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
			// Nobody is talking. Whoever wrote most recently — and is still inside their reading
			// time — has the frame instead.
			return pickWritten(now);
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
			return pickWritten(now);
		}
		return chosen;
	}

	/** The most recent message still inside its reading time; expired entries are dropped. */
	@Nullable
	private static UUID pickWritten(long now) {
		UUID best = null;
		long bestTime = 0L;
		for (Map.Entry<UUID, Written> entry : WRITTEN.entrySet()) {
			Written written = entry.getValue();
			if (now >= written.until()) {
				WRITTEN.remove(entry.getKey(), written);
				continue;
			}
			if (written.at() > bestTime) {
				bestTime = written.at();
				best = entry.getKey();
			}
		}
		return best;
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
