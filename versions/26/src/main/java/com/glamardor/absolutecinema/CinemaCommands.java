package com.glamardor.absolutecinema;

import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CameraMode;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.config.ColorGrade;
import com.glamardor.absolutecinema.config.SceneProfile;
import com.glamardor.absolutecinema.gui.ConfigScreenFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** /cinema – everything the keybinds do, plus the scene profiles and the tripod. */
public final class CinemaCommands {
	private CinemaCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				ClientCommands.literal("cinema")
						.executes(context -> openSettings())
						.then(ClientCommands.literal("on").executes(context -> setActive(true)))
						.then(ClientCommands.literal("off").executes(context -> setActive(false)))
						.then(ClientCommands.literal("toggle").executes(context -> {
							CinemaManager.toggle();
							return 1;
						}))
						.then(ClientCommands.literal("settings").executes(context -> openSettings()))
						.then(ClientCommands.literal("mode")
								.then(ClientCommands.argument("mode", StringArgumentType.word())
										.suggests((context, builder) -> {
											for (CameraMode mode : CameraMode.values()) {
												builder.suggest(mode.getId());
											}
											return builder.buildFuture();
										})
										.executes(CinemaCommands::setMode)))
						.then(ClientCommands.literal("grade")
								.then(ClientCommands.argument("grade", StringArgumentType.word())
										.suggests((context, builder) -> {
											for (ColorGrade grade : ColorGrade.values()) {
												builder.suggest(grade.getId());
											}
											return builder.buildFuture();
										})
										.executes(CinemaCommands::setGrade)))
						.then(ClientCommands.literal("scene")
								.executes(CinemaCommands::listProfiles)
								.then(ClientCommands.literal("list").executes(CinemaCommands::listProfiles))
								.then(ClientCommands.literal("save")
										.then(ClientCommands.argument("name", StringArgumentType.word())
												.executes(CinemaCommands::saveProfile)))
								.then(ClientCommands.literal("delete")
										.then(ClientCommands.argument("name", StringArgumentType.word())
												.suggests(CinemaCommands::suggestProfiles)
												.executes(CinemaCommands::deleteProfile)))
								// "set" rather than a bare name, so the suggestion list stays readable.
								.then(ClientCommands.literal("set")
										.then(ClientCommands.argument("name", StringArgumentType.word())
												.suggests(CinemaCommands::suggestProfiles)
												.executes(CinemaCommands::applyProfile))))
						.then(ClientCommands.literal("tripod")
								.executes(CinemaCommands::placeTripod))
						.then(ClientCommands.literal("shot")
								.executes(CinemaCommands::nextShot))));
	}

	// ---- scene profiles ---------------------------------------------------------------------

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
			suggestProfiles(CommandContext<FabricClientCommandSource> context,
					com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		for (SceneProfile profile : CinemaConfig.get().profiles) {
			builder.suggest(profile.name);
		}
		return builder.buildFuture();
	}

	private static int listProfiles(CommandContext<FabricClientCommandSource> context) {
		CinemaConfig config = CinemaConfig.get();
		if (config.profiles.isEmpty()) {
			context.getSource().sendFeedback(Component.translatable("absolutecinema.msg.no_profiles"));
			return 0;
		}
		for (SceneProfile profile : config.profiles) {
			boolean active = profile.name.equalsIgnoreCase(config.activeProfile);
			context.getSource().sendFeedback(Component.translatable(
					active ? "absolutecinema.msg.profile_line_active" : "absolutecinema.msg.profile_line",
					profile.name, profile.mode.getDisplayName(), profile.grade.getDisplayName()));
		}
		return config.profiles.size();
	}

	private static int applyProfile(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "name");
		if (!CinemaManager.applyProfile(name)) {
			context.getSource().sendError(Component.translatable("absolutecinema.msg.no_such_profile", name));
			return 0;
		}
		return 1;
	}

	private static int saveProfile(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "name");
		SceneProfile profile = CinemaConfig.get().saveProfile(name);
		context.getSource().sendFeedback(
				Component.translatable("absolutecinema.msg.profile_saved", profile.name));
		return 1;
	}

	private static int deleteProfile(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "name");
		if (!CinemaConfig.get().deleteProfile(name)) {
			context.getSource().sendError(Component.translatable("absolutecinema.msg.no_such_profile", name));
			return 0;
		}
		context.getSource().sendFeedback(Component.translatable("absolutecinema.msg.profile_deleted", name));
		return 1;
	}

	/** Asks the director for a different angle, the same as the key does. */
	private static int nextShot(CommandContext<FabricClientCommandSource> context) {
		CameraDirector.get().requestNewShot();
		return 1;
	}

	/** Plants the tripod at the player's eye point, wherever they are standing right now. */
	private static int placeTripod(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		CameraDirector.get().placeTripod(client.player.getEyePosition());
		context.getSource().sendFeedback(Component.translatable("absolutecinema.msg.tripod_placed"));
		return 1;
	}

	private static int setActive(boolean value) {
		CinemaManager.setActive(value);
		return 1;
	}

	private static int openSettings() {
		Minecraft client = Minecraft.getInstance();
		// Deferred: the chat screen is still closing while the command runs.
		client.schedule(() -> client.gui.setScreen(ConfigScreenFactory.create(null)));
		return 1;
	}

	private static int setMode(CommandContext<FabricClientCommandSource> context) {
		String id = StringArgumentType.getString(context, "mode");
		CameraMode mode = CameraMode.byId(id);
		CinemaManager.setMode(mode);
		context.getSource().sendFeedback(Component.translatable("absolutecinema.msg.mode", mode.getDisplayName()));
		return 1;
	}

	private static int setGrade(CommandContext<FabricClientCommandSource> context) {
		String id = StringArgumentType.getString(context, "grade");
		ColorGrade grade = ColorGrade.byId(id);
		CinemaConfig config = CinemaConfig.get();
		config.colorGrade = grade;
		config.save();
		context.getSource().sendFeedback(Component.translatable("absolutecinema.msg.grade", grade.getDisplayName()));
		return 1;
	}

}
