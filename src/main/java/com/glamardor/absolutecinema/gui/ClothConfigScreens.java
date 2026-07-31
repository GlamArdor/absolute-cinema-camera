package com.glamardor.absolutecinema.gui;

import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CameraMode;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.config.ColorGrade;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Cloth Config version of the settings screen. Loaded reflectively-by-classloading only when
 * Cloth is installed — never touch this class without checking first.
 */
final class ClothConfigScreens {
	/** Sliders are integers in Cloth, so fractions are edited as percent. */
	private static final int PERCENT = 100;

	private ClothConfigScreens() {
	}

	static Screen build(@Nullable Screen parent) {
		CinemaConfig config = CinemaConfig.get();
		CinemaConfig defaults = new CinemaConfig();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("absolutecinema.config.title"))
				.setSavingRunnable(() -> {
					config.save();
					CameraDirector.get().reset();
				});

		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Text.translatable("absolutecinema.category.general"));
		general.addEntry(entries.startEnumSelector(text("mode"), CameraMode.class, config.mode)
				.setDefaultValue(defaults.mode)
				.setEnumNameProvider(value -> ((CameraMode) value).getDisplayName())
				.setTooltip(tooltip("mode"))
				.setSaveConsumer(value -> config.mode = value)
				.build());
		general.addEntry(entries.startEnumSelector(text("grade"), ColorGrade.class, config.colorGrade)
				.setDefaultValue(defaults.colorGrade)
				.setEnumNameProvider(value -> ((ColorGrade) value).getDisplayName())
				.setTooltip(tooltip("grade"))
				.setSaveConsumer(value -> config.colorGrade = value)
				.build());
		general.addEntry(percent(entries, "grade_strength", config.gradeStrength, defaults.gradeStrength,
				value -> config.gradeStrength = value));
		general.addEntry(toggle(entries, "announce", config.announceToggle, defaults.announceToggle,
				value -> config.announceToggle = value));
		general.addEntry(toggle(entries, "exit_on_damage", config.exitOnDamage, defaults.exitOnDamage,
				value -> config.exitOnDamage = value));

		ConfigCategory filters = builder.getOrCreateCategory(Text.translatable("absolutecinema.category.filters"));
		filters.addEntry(toggle(entries, "letterbox", config.letterbox, defaults.letterbox,
				value -> config.letterbox = value));
		filters.addEntry(toggle(entries, "hide_hud", config.hideHud, defaults.hideHud,
				value -> config.hideHud = value));
		filters.addEntry(toggle(entries, "hide_hand", config.hideHand, defaults.hideHand,
				value -> config.hideHand = value));
		filters.addEntry(toggle(entries, "hide_chat", config.hideChat, defaults.hideChat,
				value -> config.hideChat = value));
		filters.addEntry(toggle(entries, "color_grading", config.colorGrading, defaults.colorGrading,
				value -> config.colorGrading = value));
		filters.addEntry(toggle(entries, "depth_of_field", config.depthOfField, defaults.depthOfField,
				value -> config.depthOfField = value));
		filters.addEntry(toggle(entries, "vignette", config.vignette, defaults.vignette,
				value -> config.vignette = value));
		filters.addEntry(toggle(entries, "film_grain", config.filmGrain, defaults.filmGrain,
				value -> config.filmGrain = value));
		filters.addEntry(toggle(entries, "smooth_camera", config.smoothCamera, defaults.smoothCamera,
				value -> config.smoothCamera = value));
		filters.addEntry(percent(entries, "letterbox_size", config.letterboxSize, defaults.letterboxSize,
				value -> config.letterboxSize = value, 0, 35));
		filters.addEntry(seconds(entries, "letterbox_fade", config.letterboxFadeSeconds, defaults.letterboxFadeSeconds,
				value -> config.letterboxFadeSeconds = value, 0, 400));

		ConfigCategory dof = builder.getOrCreateCategory(Text.translatable("absolutecinema.category.dof"));
		dof.addEntry(percent(entries, "dof_strength", config.dofStrength, defaults.dofStrength,
				value -> config.dofStrength = value));
		dof.addEntry(toggle(entries, "dof_auto_focus", config.dofAutoFocus, defaults.dofAutoFocus,
				value -> config.dofAutoFocus = value));
		dof.addEntry(blocks(entries, "dof_focus_distance", config.dofFocusDistance, defaults.dofFocusDistance,
				value -> config.dofFocusDistance = value, 1, 64));
		dof.addEntry(blocks(entries, "dof_focus_range", config.dofFocusRange, defaults.dofFocusRange,
				value -> config.dofFocusRange = value, 1, 32));
		dof.addEntry(toggle(entries, "dof_foreground", config.dofBlurForeground, defaults.dofBlurForeground,
				value -> config.dofBlurForeground = value));
		dof.addEntry(percent(entries, "dof_foreground_amount", config.dofForegroundAmount,
				defaults.dofForegroundAmount, value -> config.dofForegroundAmount = value));

		ConfigCategory firstPerson = builder.getOrCreateCategory(Text.translatable("absolutecinema.category.first_person"));
		firstPerson.addEntry(percent(entries, "rotation_smoothing", config.rotationSmoothing, defaults.rotationSmoothing,
				value -> config.rotationSmoothing = value, 0, 95));
		firstPerson.addEntry(percent(entries, "position_smoothing", config.positionSmoothing, defaults.positionSmoothing,
				value -> config.positionSmoothing = value, 0, 95));
		firstPerson.addEntry(toggle(entries, "stabilize", config.stabilizeCamera, defaults.stabilizeCamera,
				value -> config.stabilizeCamera = value));

		ConfigCategory directed = builder.getOrCreateCategory(Text.translatable("absolutecinema.category.directed"));
		directed.addEntry(seconds(entries, "shot_duration", config.shotDuration, defaults.shotDuration,
				value -> config.shotDuration = value, 200, 6000));
		directed.addEntry(blocks(entries, "shot_distance", config.shotDistance, defaults.shotDistance,
				value -> config.shotDistance = value, 1, 16));
		directed.addEntry(percent(entries, "shot_speed", config.shotSpeed, defaults.shotSpeed,
				value -> config.shotSpeed = value, 20, 300));
		directed.addEntry(toggle(entries, "hard_cuts", config.hardCuts, defaults.hardCuts,
				value -> config.hardCuts = value));
		directed.addEntry(seconds(entries, "transition", config.transitionSeconds, defaults.transitionSeconds,
				value -> config.transitionSeconds = value, 20, 500));
		directed.addEntry(toggle(entries, "handheld_drift", config.handheldDrift, defaults.handheldDrift,
				value -> config.handheldDrift = value));
		directed.addEntry(toggle(entries, "avoid_walls", config.avoidWalls, defaults.avoidWalls,
				value -> config.avoidWalls = value));
		directed.addEntry(blocks(entries, "scene_radius", config.sceneRadius, defaults.sceneRadius,
				value -> config.sceneRadius = value, 3, 48));
		directed.addEntry(toggle(entries, "include_named", config.includeNamedEntities,
				defaults.includeNamedEntities, value -> config.includeNamedEntities = value));
		directed.addEntry(toggle(entries, "keep_everyone", config.keepEveryoneInFrame,
				defaults.keepEveryoneInFrame, value -> config.keepEveryoneInFrame = value));
		directed.addEntry(toggle(entries, "hide_blockers", config.hideNearbyBlockers,
				defaults.hideNearbyBlockers, value -> config.hideNearbyBlockers = value));
		directed.addEntry(blocks(entries, "blocker_distance", config.blockerDistance, defaults.blockerDistance,
				value -> config.blockerDistance = value, 1, 5));

		ConfigCategory speaker = builder.getOrCreateCategory(Text.translatable("absolutecinema.category.speaker"));
		speaker.addEntry(seconds(entries, "speaker_hold", config.speakerHoldSeconds, defaults.speakerHoldSeconds,
				value -> config.speakerHoldSeconds = value, 20, 1500));
		speaker.addEntry(seconds(entries, "speaker_handover", config.speakerHandoverSeconds,
				defaults.speakerHandoverSeconds, value -> config.speakerHandoverSeconds = value, 0, 300));
		speaker.addEntry(seconds(entries, "min_shot", config.minShotSeconds, defaults.minShotSeconds,
				value -> config.minShotSeconds = value, 0, 500));
		speaker.addEntry(seconds(entries, "max_focus", config.maxSpeakerFocusSeconds,
				defaults.maxSpeakerFocusSeconds, value -> config.maxSpeakerFocusSeconds = value, 0, 12000));
		speaker.addEntry(seconds(entries, "speaker_break", config.speakerBreakSeconds,
				defaults.speakerBreakSeconds, value -> config.speakerBreakSeconds = value, 100, 6000));
		speaker.addEntry(blocks(entries, "speaker_distance", config.speakerMaxDistance, defaults.speakerMaxDistance,
				value -> config.speakerMaxDistance = value, 4, 64));
		speaker.addEntry(toggle(entries, "rule_of_thirds", config.ruleOfThirds, defaults.ruleOfThirds,
				value -> config.ruleOfThirds = value));

		return builder.build();
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> toggle(ConfigEntryBuilder entries,
			String key, boolean value, boolean fallback, Consumer<Boolean> save) {
		return entries.startBooleanToggle(text(key), value)
				.setDefaultValue(fallback)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> percent(ConfigEntryBuilder entries,
			String key, float value, float fallback, Consumer<Float> save) {
		return percent(entries, key, value, fallback, save, 0, 100);
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> percent(ConfigEntryBuilder entries,
			String key, float value, float fallback, Consumer<Float> save, int min, int max) {
		return entries.startIntSlider(text(key), Math.round(value * PERCENT), min, max)
				.setDefaultValue(Math.round(fallback * PERCENT))
				.setTextGetter(v -> Text.literal(v + "%"))
				.setTooltip(tooltip(key))
				.setSaveConsumer(v -> save.accept(v / (float) PERCENT))
				.build();
	}

	/** Seconds edited in hundredths so the slider has a usable resolution. */
	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> seconds(ConfigEntryBuilder entries,
			String key, float value, float fallback, Consumer<Float> save, int min, int max) {
		return entries.startIntSlider(text(key), Math.round(value * PERCENT), min, max)
				.setDefaultValue(Math.round(fallback * PERCENT))
				.setTextGetter(v -> Text.translatable("absolutecinema.unit.seconds", String.format("%.1f", v / 100.0f)))
				.setTooltip(tooltip(key))
				.setSaveConsumer(v -> save.accept(v / (float) PERCENT))
				.build();
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> blocks(ConfigEntryBuilder entries,
			String key, float value, float fallback, Consumer<Float> save, int min, int max) {
		return entries.startIntSlider(text(key), Math.round(value), min, max)
				.setDefaultValue(Math.round(fallback))
				.setTextGetter(v -> Text.translatable("absolutecinema.unit.blocks", v))
				.setTooltip(tooltip(key))
				.setSaveConsumer(v -> save.accept((float) v))
				.build();
	}

	private static Text text(String key) {
		return Text.translatable("absolutecinema.option." + key);
	}

	private static Text[] tooltip(String key) {
		String path = "absolutecinema.option." + key + ".tooltip";
		return new Text[] { Text.translatable(path) };
	}

	@SuppressWarnings("unused")
	private static <T> Supplier<T> constant(T value) {
		return () -> value;
	}
}
