package com.glamardor.absolutecinema.chat;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.voice.SpeakerTracker;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Turns what people write into something the camera can point at.
 *
 * <p>On a roleplay server a good half of every scene is played out in text — <code>/me</code>,
 * <code>/do</code>, an ordinary line of dialogue — and a camera that only listens for voices spends
 * that half filming whoever spoke last, or nobody at all.
 *
 * <p>Finding the author is the whole problem, and it has two answers. Genuine player chat carries
 * the sender's uuid in the packet, so the client already knows. Anything a plugin has reformatted
 * arrives as a system message instead: a piece of text, with no author attached to it anywhere.
 * What saves us there is that servers habitually attach a <code>show_entity</code> hover to the
 * name — the tooltip you get pointing at somebody's message — and that hover is, by its own
 * definition, an entity type and a uuid. So the author is read out of the decoration the server
 * was already sending, with nothing asked of the server at all.
 *
 * <p>A message is not speech, and is not treated as it. Speech is a duration: it keeps arriving and
 * the camera holds while it does. A message is an instant with a reading time attached — a short
 * reply is worth a couple of seconds, a long emote is worth reading through — and a live voice
 * always outranks it.
 */
public final class ChatWatcher {
	private ChatWatcher() {
	}

	public static void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
				handle(message, authorOf(sender, signed)));
		// Overlay messages are the action bar, not chat — nobody plays a scene up there.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message, null);
			}
		});
	}

	@Nullable
	private static UUID authorOf(@Nullable GameProfile sender, @Nullable SignedMessage signed) {
		if (sender != null) {
			return sender.getId();
		}
		return signed == null ? null : signed.getSender();
	}

	private static void handle(Text message, @Nullable UUID known) {
		// A message from a stranger must never be able to break the frame, let alone the game.
		try {
			consider(message, known);
		} catch (Throwable error) {
			AbsoluteCinema.LOGGER.warn("could not read an incoming message", error);
		}
	}

	private static void consider(Text message, @Nullable UUID known) {
		CinemaConfig config = CinemaConfig.get();
		if (!config.reactToChat) {
			return;
		}
		UUID author = resolveAuthor(message, known);
		if (author == null) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return;
		}
		if (!config.reactToOwnChat && author.equals(client.player.getUuid())) {
			return;
		}

		// Somebody out of sight, or halfway across the map on a global channel, is not in the shot
		// and cannot be cut to.
		PlayerEntity player = world.getPlayerByUuid(author);
		if (player == null || !player.isAlive()) {
			return;
		}
		double reach = config.speakerMaxDistance;
		if (player.squaredDistanceTo(client.player) > reach * reach) {
			return;
		}

		String plain = message.getString();
		if (ignored(plain, config)) {
			return;
		}

		SpeakerTracker.markWritten(author, readingTime(plain, config));
	}

	/**
	 * How long the frame is theirs: long enough to read what they wrote, and no longer. A held
	 * frame on somebody who typed "ага" is as wrong as cutting away mid-paragraph.
	 */
	private static float readingTime(String plain, CinemaConfig config) {
		float length = config.chatSecondsPer100 * plain.length() / 100.0f;
		return Math.min(config.chatHoldSeconds + length, config.chatMaxSeconds);
	}

	/**
	 * Whether the player has asked for this message to be left alone.
	 *
	 * <p>A marker matches when it appears anywhere in the line, and a <code>*</code> inside it
	 * stands for any run of text. That second part is what makes the setting usable: an
	 * out-of-character aside is written <code>((like this))</code>, and the useful rule is "any
	 * line with double brackets round part of it" — <code>((*))</code> — not the literal characters
	 * <code>(())</code>, which never occur in a real message.
	 */
	private static boolean ignored(String plain, CinemaConfig config) {
		if (config.chatIgnore.isEmpty()) {
			return false;
		}
		String haystack = plain.toLowerCase(Locale.ROOT);
		for (String marker : config.chatIgnore) {
			String needle = marker.toLowerCase(Locale.ROOT);
			if (needle.indexOf('*') < 0) {
				if (haystack.contains(needle)) {
					return true;
				}
				continue;
			}
			if (matchesWildcard(haystack, needle)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The pieces between the stars, in order, anywhere in the line.
	 *
	 * <p>Nothing is anchored to the start or the end, because the line never begins with the
	 * message: the server prints its own prefix and the speaker's name first, and a marker that
	 * only matched from the very beginning would never match anything at all.
	 */
	private static boolean matchesWildcard(String haystack, String pattern) {
		int at = 0;
		for (String piece : pattern.split("\\*")) {
			if (piece.isEmpty()) {
				continue;
			}
			int found = haystack.indexOf(piece, at);
			if (found < 0) {
				return false;
			}
			at = found + piece.length();
		}
		return true;
	}

	/**
	 * Who wrote this, by whatever evidence the message carries.
	 *
	 * <p>Also used by the chat probe, so a dump always reports exactly what the camera would do.
	 *
	 * <p>A message with no tag has no author here, and deliberately so. The name is printed in the
	 * line, and it would be possible to go looking for it — but "Someone looks at Another" is
	 * written by Someone, and a camera that reads the wrong name cuts to the wrong person, which is
	 * worse than not cutting at all. The server marks its own messages; anything it has not marked
	 * is left alone.
	 */
	@Nullable
	public static UUID resolveAuthor(Text message, @Nullable UUID known) {
		return known != null ? known : findAuthorTag(message);
	}

	/**
	 * The first player named by a show_entity hover, depth first.
	 *
	 * <p>First, not best: the server puts the author's tag on the name it prints at the front of
	 * the line, so the author comes before anybody the message happens to mention. A tag naming
	 * somebody who is not a player — a horse, a hostile mob — is not an author and is skipped.
	 */
	@Nullable
	private static UUID findAuthorTag(Text node) {
		if (node.getStyle().getHoverEvent() instanceof HoverEvent.ShowEntity show
				&& isPlayer(show.entity().uuid)) {
			return show.entity().uuid;
		}
		for (Text child : node.getSiblings()) {
			UUID found = findAuthorTag(child);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static boolean isPlayer(UUID uuid) {
		ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
		return handler != null && handler.getPlayerListEntry(uuid) != null;
	}

}
