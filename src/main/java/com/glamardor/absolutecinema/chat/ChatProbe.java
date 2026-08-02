package com.glamardor.absolutecinema.chat;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EntityType;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * A diagnostic recorder for incoming chat, written for one question: when a roleplay server sends
 * a <code>/me</code>, what does the client actually receive, and is the author identifiable?
 *
 * <p>The answer is not the same everywhere. Genuine player chat arrives as a signed message and
 * carries the sender's uuid in the protocol itself, so nothing is needed from the server. Anything
 * a plugin has reformatted normally arrives as a system message instead, which is a bare piece of
 * text with no author attached — there the server has to tag it, and this recorder shows whether
 * it already does.
 *
 * <p>So every message is written out whole: which packet carried it, whether a sender came with
 * it, the full style tree with its hover, click and insertion payloads, and the raw json. The last
 * line of each entry is the verdict — who the camera would follow, and on what evidence.
 */
public final class ChatProbe {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final int MAX_TREE_NODES = 60;

	private static boolean recording;
	private static int remaining;
	private static int seen;
	@Nullable
	private static Path file;

	/**
	 * Guards against the recorder recording itself. Its own progress line goes into the chat hud
	 * directly rather than through {@code sendMessage}, because that one is routed through the
	 * client's message handler — which is exactly where the event we listen on is fired from.
	 */
	private static boolean emitting;

	private ChatProbe() {
	}

	/** Registered once at start-up; the listeners cost nothing until the recorder is armed. */
	public static void register() {
		ClientReceiveMessageEvents.CHAT.register(
				(message, signed, sender, params, timestamp) -> record(message, signed, sender, params, false));
		ClientReceiveMessageEvents.GAME.register(
				(message, overlay) -> record(message, null, null, null, overlay));
	}

	public static boolean isRecording() {
		return recording;
	}

	/** Arms the recorder for the next {@code count} messages and returns the file they land in. */
	public static Path start(int count) {
		file = FabricLoader.getInstance().getGameDir().resolve("absolutecinema-chatdump.txt");
		recording = true;
		remaining = count;
		seen = 0;
		write("\n========================================================================\n"
				+ "absolute cinema chat probe — " + LocalTime.now().format(TIME)
				+ ", recording " + count + " messages\n"
				+ "========================================================================\n");
		return file;
	}

	public static int stop() {
		recording = false;
		int total = seen;
		write("---- stopped after " + total + " messages ----\n");
		return total;
	}

	// -------------------------------------------------------------------------------------------

	private static void record(Text message, @Nullable SignedMessage signed, @Nullable GameProfile sender,
			@Nullable MessageType.Parameters params, boolean overlay) {
		if (!recording || emitting) {
			return;
		}
		// A diagnostic must never be the reason somebody's game stops: whatever a server sends,
		// the worst this may do is write a line saying it could not read it.
		try {
			describe(message, signed, sender, params, overlay);
		} catch (Throwable error) {
			recording = false;
			AbsoluteCinema.LOGGER.error("the chat probe failed and has switched itself off", error);
			write("---- the probe failed on message #" + seen + ": " + error + " ----\n");
		}
	}

	private static void describe(Text message, @Nullable SignedMessage signed, @Nullable GameProfile sender,
			@Nullable MessageType.Parameters params, boolean overlay) {
		seen++;
		// Counted down before anything is printed, so a mistake here can cost one message rather
		// than an endless stream of them.
		boolean last = --remaining <= 0;
		if (last) {
			recording = false;
		}
		StringBuilder out = new StringBuilder(1024);
		String kind = params != null ? "CHAT (signed player chat)" : "GAME (system message)";
		out.append("\n=== #").append(seen).append("  ").append(kind)
				.append(params == null ? ", overlay=" + overlay : "")
				.append("  ").append(LocalTime.now().format(TIME)).append(" ===\n");

		out.append("  sender profile : ").append(sender == null
				? "— (none: the packet carried no author)"
				: sender.getName() + " / " + sender.getId()).append('\n');
		if (signed != null) {
			out.append("  signed         : uuid=").append(signed.getSender())
					.append("  content=\"").append(signed.getSignedContent()).append("\"\n");
		}
		if (params != null) {
			out.append("  chat type      : ").append(params.type().getIdAsString())
					.append("  name=\"").append(params.name().getString()).append('"')
					.append(params.targetName().map(t -> "  target=\"" + t.getString() + '"').orElse(""))
					.append('\n');
		}
		out.append("  plain text     : \"").append(message.getString()).append("\"\n");

		out.append("  style tree:\n");
		walk(message, 0, new int[] {0}, out);

		out.append("  json: ").append(toJson(message)).append('\n');
		out.append("  ==> ").append(verdict(message, sender, signed)).append('\n');

		write(out.toString());
		announce(kind, sender, message);

		if (last) {
			write("---- finished: " + seen + " messages recorded ----\n");
			show(Text.translatable("absolutecinema.msg.chatdump_done", seen));
		}
	}

	/** Depth-first through the component tree, because the tag may sit on any single run. */
	private static void walk(Text node, int depth, int[] budget, StringBuilder out) {
		if (budget[0]++ > MAX_TREE_NODES) {
			return;
		}
		out.append("    ");
		out.append("  ".repeat(depth));
		out.append("· ").append(node.getContent());
		String style = describe(node.getStyle());
		if (!style.isEmpty()) {
			out.append("  [").append(style).append(']');
		}
		out.append('\n');
		for (Text child : node.getSiblings()) {
			walk(child, depth + 1, budget, out);
		}
	}

	/** Only the parts of a style that could carry an author; colours are noise here. */
	private static String describe(Style style) {
		StringBuilder parts = new StringBuilder();
		HoverEvent hover = style.getHoverEvent();
		if (hover instanceof HoverEvent.ShowEntity show) {
			HoverEvent.EntityContent entity = show.entity();
			parts.append("hover=show_entity{").append(EntityType.getId(entity.entityType))
					.append(' ').append(entity.uuid)
					.append(entity.name.map(n -> " \"" + n.getString() + '"').orElse(""))
					.append('}');
		} else if (hover instanceof HoverEvent.ShowText show) {
			parts.append("hover=show_text{\"").append(show.value().getString()).append("\"}");
		} else if (hover != null) {
			parts.append("hover=").append(hover.getAction());
		}
		String insertion = style.getInsertion();
		if (insertion != null) {
			append(parts, "insertion=\"" + insertion + '"');
		}
		ClickEvent click = style.getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			append(parts, "click=run_command{\"" + run.command() + "\"}");
		} else if (click instanceof ClickEvent.SuggestCommand suggest) {
			append(parts, "click=suggest_command{\"" + suggest.command() + "\"}");
		} else if (click != null) {
			append(parts, "click=" + click.getAction());
		}
		return parts.toString();
	}

	private static void append(StringBuilder parts, String piece) {
		if (!parts.isEmpty()) {
			parts.append(' ');
		}
		parts.append(piece);
	}

	/**
	 * What the camera would do with this message today, in the order the real feature will use:
	 * the signed sender first, then a show_entity tag, then an insertion tag, then — reluctantly —
	 * a nickname found in the text.
	 */
	private static String verdict(Text message, @Nullable GameProfile sender, @Nullable SignedMessage signed) {
		if (sender != null) {
			return "IDENTIFIED from the packet: " + sender.getName() + " (" + sender.getId()
					+ ") — no server-side tagging needed for this kind of message";
		}
		if (signed != null) {
			return "IDENTIFIED from the signature: " + signed.getSender();
		}
		UUID tagged = findEntityTag(message);
		if (tagged != null) {
			return "IDENTIFIED from a show_entity hover: " + tagged + describeKnown(tagged);
		}
		String insertion = findInsertion(message);
		if (insertion != null) {
			return "insertion present, could be used if agreed: \"" + insertion + '"';
		}
		String guess = guessByName(message.getString());
		if (guess != null) {
			return "NOT identified — a nickname appears in the text (" + guess
					+ ") but the server tagged nobody; the camera will not react";
		}
		return "NOT identified — nothing in this message says who wrote it";
	}

	@Nullable
	private static UUID findEntityTag(Text node) {
		if (node.getStyle().getHoverEvent() instanceof HoverEvent.ShowEntity show) {
			return show.entity().uuid;
		}
		for (Text child : node.getSiblings()) {
			UUID found = findEntityTag(child);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	@Nullable
	private static String findInsertion(Text node) {
		String own = node.getStyle().getInsertion();
		if (own != null) {
			return own;
		}
		for (Text child : node.getSiblings()) {
			String found = findInsertion(child);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** Earliest nickname from the tab list that appears in the text. The last resort, and weak. */
	@Nullable
	private static String guessByName(String plain) {
		ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
		if (handler == null) {
			return null;
		}
		String haystack = plain.toLowerCase(Locale.ROOT);
		String best = null;
		int bestAt = Integer.MAX_VALUE;
		for (PlayerListEntry entry : handler.getPlayerList()) {
			String name = entry.getProfile().getName();
			int at = haystack.indexOf(name.toLowerCase(Locale.ROOT));
			if (at >= 0 && at < bestAt) {
				bestAt = at;
				best = name;
			}
		}
		return best;
	}

	private static String describeKnown(UUID uuid) {
		ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
		if (handler == null) {
			return "";
		}
		PlayerListEntry entry = handler.getPlayerListEntry(uuid);
		if (entry == null) {
			return " — WARNING: no such player in the tab list";
		}
		boolean loaded = handler.getWorld() != null && handler.getWorld().getPlayerByUuid(uuid) != null;
		return " = " + entry.getProfile().getName() + (loaded ? ", in render distance" : ", not loaded here");
	}

	private static String toJson(Text message) {
		ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
		if (handler == null) {
			return "(no connection)";
		}
		return TextCodecs.CODEC
				.encodeStart(RegistryOps.of(JsonOps.INSTANCE, handler.getRegistryManager()), message)
				.result()
				.map(JsonElement::toString)
				.orElse("(could not be encoded)");
	}

	/** A single short line in chat, so it is obvious the recorder is catching anything at all. */
	private static void announce(String kind, @Nullable GameProfile sender, Text message) {
		String who = sender != null ? sender.getName()
				: findEntityTag(message) != null ? "hover tag" : "unknown";
		String plain = message.getString();
		if (plain.length() > 40) {
			plain = plain.substring(0, 40) + "…";
		}
		show(Text.literal("[dump #" + seen + "] " + kind.substring(0, kind.indexOf(' '))
				+ " · " + who + " · " + plain));
	}

	/**
	 * Straight into the chat hud. Deliberately not {@code player.sendMessage}: that goes through
	 * the message handler, the event we listen on fires from there, and the recorder would then
	 * record its own line, print another, and keep going until the stack ran out.
	 */
	private static void show(Text line) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.inGameHud == null) {
			return;
		}
		emitting = true;
		try {
			client.inGameHud.getChatHud().addMessage(line);
		} finally {
			emitting = false;
		}
	}

	private static void write(String text) {
		if (file == null) {
			return;
		}
		try {
			Files.writeString(file, text, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException error) {
			AbsoluteCinema.LOGGER.warn("could not write the chat dump", error);
		}
	}
}
