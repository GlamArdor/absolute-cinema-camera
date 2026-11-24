package com.glamardor.absolutecinema.gui;

import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CameraMode;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.config.ColorGrade;
import com.glamardor.absolutecinema.render.SceneDome;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The settings screen used when Cloth Config is not installed. Plain vanilla widgets, one
 * scrolling list, same options as the Cloth version.
 */
public class FallbackConfigScreen extends Screen {
	private static final int ROW_WIDTH = 310;

	@Nullable
	private final Screen parent;
	private final CinemaConfig config = CinemaConfig.get();
	private OptionList list;

	public FallbackConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("absolutecinema.config.title"));
		this.parent = parent;
	}

	/**
	 * Leaves the world visible behind this screen while the scene radius is being shown.
	 *
	 * <p>A screen opened in game does not blur what is behind it – it lays a dark gradient over the
	 * whole window, which is a different method from the blurring a screen does over the menus, and
	 * it was covering the very thing the radius slider is for.
	 */
	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (SceneDome.isShowing()) {
			return;
		}
		super.extractBackground(context, mouseX, mouseY, delta);
	}

	@Override
	protected void init() {
		list = new OptionList(this.minecraft, this.width, this.height - 96, 40, 25);

		list.addHeader(Component.translatable("absolutecinema.category.general"));
		list.addWidget(cycleButton("mode", () -> config.mode.getDisplayName(), () -> {
			config.mode = config.mode.next();
			CameraDirector.get().reset();
		}));
		list.addWidget(cycleButton("grade", () -> config.colorGrade.getDisplayName(),
				() -> config.colorGrade = config.colorGrade.next()));
		list.addWidget(percentSlider("grade_strength", config.gradeStrength, 0.0f, 1.0f,
				value -> config.gradeStrength = value));
		list.addWidget(toggle("announce", () -> config.announceToggle, value -> config.announceToggle = value));
		list.addWidget(toggle("exit_on_damage", () -> config.exitOnDamage, value -> config.exitOnDamage = value));

		list.addHeader(Component.translatable("absolutecinema.category.filters"));
		list.addWidget(toggle("letterbox", () -> config.letterbox, value -> config.letterbox = value));
		list.addWidget(toggle("hide_hud", () -> config.hideHud, value -> config.hideHud = value));
		list.addWidget(toggle("hide_hand", () -> config.hideHand, value -> config.hideHand = value));
		list.addWidget(toggle("hide_chat", () -> config.hideChat, value -> config.hideChat = value));
		list.addWidget(toggle("color_grading", () -> config.colorGrading, value -> config.colorGrading = value));
		list.addWidget(toggle("depth_of_field", () -> config.depthOfField, value -> config.depthOfField = value));
		list.addWidget(toggle("vignette", () -> config.vignette, value -> config.vignette = value));
		list.addWidget(toggle("film_grain", () -> config.filmGrain, value -> config.filmGrain = value));
		list.addWidget(toggle("smooth_camera", () -> config.smoothCamera, value -> config.smoothCamera = value));
		list.addWidget(percentSlider("letterbox_size", config.letterboxSize, 0.0f, 0.35f,
				value -> config.letterboxSize = value));
		list.addWidget(secondsSlider("letterbox_fade", config.letterboxFadeSeconds, 0.0f, 4.0f,
				value -> config.letterboxFadeSeconds = value));

		list.addHeader(Component.translatable("absolutecinema.category.dof"));
		list.addWidget(percentSlider("dof_strength", config.dofStrength, 0.0f, 1.0f,
				value -> config.dofStrength = value));
		list.addWidget(toggle("dof_auto_focus", () -> config.dofAutoFocus, value -> config.dofAutoFocus = value));
		list.addWidget(blocksSlider("dof_focus_distance", config.dofFocusDistance, 1.0f, 64.0f,
				value -> config.dofFocusDistance = value));
		list.addWidget(blocksSlider("dof_focus_range", config.dofFocusRange, 1.0f, 32.0f,
				value -> config.dofFocusRange = value));
		list.addWidget(toggle("dof_foreground", () -> config.dofBlurForeground,
				value -> config.dofBlurForeground = value));
		list.addWidget(percentSlider("dof_foreground_amount", config.dofForegroundAmount, 0.0f, 1.0f,
				value -> config.dofForegroundAmount = value));

		list.addHeader(Component.translatable("absolutecinema.category.first_person"));
		list.addWidget(percentSlider("rotation_smoothing", config.rotationSmoothing, 0.0f, 0.95f,
				value -> config.rotationSmoothing = value));
		list.addWidget(percentSlider("position_smoothing", config.positionSmoothing, 0.0f, 0.95f,
				value -> config.positionSmoothing = value));
		list.addWidget(toggle("stabilize", () -> config.stabilizeCamera, value -> config.stabilizeCamera = value));

		list.addHeader(Component.translatable("absolutecinema.category.directed"));
		list.addWidget(secondsSlider("shot_duration", config.shotDuration, 2.0f, 60.0f,
				value -> config.shotDuration = value));
		list.addWidget(blocksSlider("shot_distance", config.shotDistance, 1.0f, 16.0f,
				value -> config.shotDistance = value));
		list.addWidget(percentSlider("shot_speed", config.shotSpeed, 0.2f, 3.0f,
				value -> config.shotSpeed = value));
		list.addWidget(toggle("hard_cuts", () -> config.hardCuts, value -> config.hardCuts = value));
		list.addWidget(secondsSlider("transition", config.transitionSeconds, 0.2f, 5.0f,
				value -> config.transitionSeconds = value));
		list.addWidget(toggle("handheld_drift", () -> config.handheldDrift, value -> config.handheldDrift = value));
		list.addWidget(toggle("avoid_walls", () -> config.avoidWalls, value -> config.avoidWalls = value));
		list.addWidget(blocksSlider("scene_radius", config.sceneRadius, 3.0f, 48.0f,
				value -> config.sceneRadius = value));
		list.addWidget(blocksSlider("scene_height", config.sceneHeightLimit, 0.0f, 32.0f,
				value -> config.sceneHeightLimit = value));
		list.addWidget(blocksSlider("leave_scene", config.leaveSceneDistance, 0.0f, 48.0f,
				value -> config.leaveSceneDistance = value));
		list.addWidget(blocksSlider("camera_height", config.cameraHeight, -2.0f, 2.0f,
				value -> config.cameraHeight = value));
		list.addWidget(toggle("include_named", () -> config.includeNamedEntities,
				value -> config.includeNamedEntities = value));
		list.addWidget(toggle("manual_shots", () -> config.manualShotChanges,
				value -> config.manualShotChanges = value));
		list.addWidget(toggle("keep_everyone", () -> config.keepEveryoneInFrame,
				value -> config.keepEveryoneInFrame = value));
		list.addWidget(toggle("hide_blockers", () -> config.hideNearbyBlockers,
				value -> config.hideNearbyBlockers = value));
		list.addWidget(blocksSlider("blocker_distance", config.blockerDistance, 0.5f, 5.0f,
				value -> config.blockerDistance = value));
		list.addWidget(toggle("fade_players", () -> config.fadeNearbyPlayers,
				value -> config.fadeNearbyPlayers = value));
		list.addWidget(blocksSlider("player_fade_distance", config.playerFadeDistance, 0.5f, 8.0f,
				value -> config.playerFadeDistance = value));

		list.addHeader(Component.translatable("absolutecinema.category.speaker"));
		list.addWidget(toggle("react_to_chat", () -> config.reactToChat,
				value -> config.reactToChat = value));
		list.addWidget(toggle("react_to_own_chat", () -> config.reactToOwnChat,
				value -> config.reactToOwnChat = value));
		list.addWidget(toggle("dynamic_follows", () -> config.dynamicFollowsSpeaker,
				value -> config.dynamicFollowsSpeaker = value));
		list.addWidget(secondsSlider("chat_hold", config.chatHoldSeconds, 0.0f, 20.0f,
				value -> config.chatHoldSeconds = value));
		list.addWidget(secondsSlider("chat_per_100", config.chatSecondsPer100, 0.0f, 30.0f,
				value -> config.chatSecondsPer100 = value));
		list.addWidget(secondsSlider("chat_max", config.chatMaxSeconds, 0.5f, 60.0f,
				value -> config.chatMaxSeconds = value));
		list.addWidget(textField("chat_ignore", String.join(", ", config.chatIgnore),
				value -> config.chatIgnore = splitMarkers(value)));
		list.addWidget(secondsSlider("speaker_hold", config.speakerHoldSeconds, 0.2f, 15.0f,
				value -> config.speakerHoldSeconds = value));
		list.addWidget(secondsSlider("speaker_handover", config.speakerHandoverSeconds, 0.0f, 3.0f,
				value -> config.speakerHandoverSeconds = value));
		list.addWidget(secondsSlider("min_shot", config.minShotSeconds, 0.0f, 5.0f,
				value -> config.minShotSeconds = value));
		list.addWidget(secondsSlider("max_focus", config.maxSpeakerFocusSeconds, 0.0f, 120.0f,
				value -> config.maxSpeakerFocusSeconds = value));
		list.addWidget(secondsSlider("speaker_break", config.speakerBreakSeconds, 1.0f, 60.0f,
				value -> config.speakerBreakSeconds = value));
		list.addWidget(blocksSlider("speaker_distance", config.speakerMaxDistance, 4.0f, 64.0f,
				value -> config.speakerMaxDistance = value));
		list.addWidget(toggle("rule_of_thirds", () -> config.ruleOfThirds, value -> config.ruleOfThirds = value));

		addRenderableWidget(list);

		Button reset = Button.builder(Component.translatable("absolutecinema.config.reset"), button -> {
			config.resetToDefaults();
			CameraDirector.get().reset();
			// Rebuild so every widget shows the value it was just reset to.
			rebuildWidgets();
		}).bounds(this.width / 2 - 154, this.height - 30, 150, 20).build();
		reset.setTooltip(Tooltip.create(Component.translatable("absolutecinema.config.reset.tooltip")));
		addRenderableWidget(reset);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(this.width / 2 + 4, this.height - 30, 150, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
		if (!ConfigScreenFactory.isClothPresent()) {
			context.centeredText(this.font,
					Component.translatable("absolutecinema.config.no_cloth"), this.width / 2, this.height - 44, 0xFF9A9A9A);
		}
	}

	@Override
	public void onClose() {
		config.save();
		CameraDirector.get().reset();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	/** Commas separate the markers; a marker may still contain spaces, so only the commas count. */
	private static List<String> splitMarkers(String value) {
		List<String> markers = new java.util.ArrayList<>();
		for (String piece : value.split(",")) {
			String marker = piece.trim();
			if (!marker.isEmpty()) {
				markers.add(marker);
			}
		}
		return markers;
	}

	private AbstractWidget toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return cycleButton(key,
				() -> getter.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF,
				() -> setter.accept(!getter.get()));
	}

	/**
	 * A plain text box, for the one setting that is a list of words rather than a number.
	 *
	 * <p>Written back on every keystroke, like everything else on this screen: there is no save
	 * button here, and a field that only committed on enter would silently lose what was typed.
	 */
	private AbstractWidget textField(String key, String current, Consumer<String> setter) {
		EditBox field = new EditBox(this.font, 0, 0, ROW_WIDTH, 20,
				Component.translatable("absolutecinema.option." + key));
		field.setMaxLength(256);
		field.setValue(current);
		field.setResponder(setter);
		field.setTooltip(Tooltip.create(Component.translatable("absolutecinema.option." + key + ".tooltip")));
		return field;
	}

	private AbstractWidget cycleButton(String key, Supplier<Component> value, Runnable onClick) {
		Button button = Button.builder(label(key, value.get()), b -> {
			onClick.run();
			b.setMessage(label(key, value.get()));
		}).bounds(0, 0, ROW_WIDTH, 20).build();
		button.setTooltip(Tooltip.create(Component.translatable("absolutecinema.option." + key + ".tooltip")));
		return button;
	}

	private AbstractWidget percentSlider(String key, float current, float min, float max, Consumer<Float> setter) {
		return new OptionSlider(key, current, min, max, setter,
				value -> Component.literal(Math.round(value * 100.0f) + "%"));
	}

	private AbstractWidget secondsSlider(String key, float current, float min, float max, Consumer<Float> setter) {
		return new OptionSlider(key, current, min, max, setter,
				value -> Component.translatable("absolutecinema.unit.seconds", String.format("%.1f", value)));
	}

	private AbstractWidget blocksSlider(String key, float current, float min, float max, Consumer<Float> setter) {
		return new OptionSlider(key, current, min, max, setter,
				value -> Component.translatable("absolutecinema.unit.blocks", Math.round(value)));
	}

	private static Component label(String key, Component value) {
		return Component.translatable("absolutecinema.option." + key).append(": ").append(value);
	}

	/** Slider over a float range that writes straight back into the config object. */
	private static class OptionSlider extends AbstractSliderButton {
		private final String key;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;
		private final Function<Float, Component> display;

		OptionSlider(String key, float current, float min, float max, Consumer<Float> setter,
				Function<Float, Component> display) {
			super(0, 0, ROW_WIDTH, 20, Component.empty(),
					Mth.clamp((current - min) / (max - min), 0.0f, 1.0f));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.display = display;
			setTooltip(Tooltip.create(Component.translatable("absolutecinema.option." + key + ".tooltip")));
			updateMessage();
		}

		private float currentValue() {
			return (float) (min + (max - min) * this.value);
		}

		@Override
		protected void updateMessage() {
			setMessage(label(key, display.apply(currentValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(currentValue());
		}
	}

	private static class OptionList extends ContainerObjectSelectionList<OptionList.Entry> {
		OptionList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(AbstractWidget widget) {
			addEntry(new WidgetEntry(widget));
		}

		void addHeader(Component text) {
			addEntry(new HeaderEntry(text));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		abstract static class Entry extends ContainerObjectSelectionList.Entry<com.glamardor.absolutecinema.gui.FallbackConfigScreen.OptionList.Entry> {
		}

		static class WidgetEntry extends com.glamardor.absolutecinema.gui.FallbackConfigScreen.OptionList.Entry {
			private final AbstractWidget widget;

			WidgetEntry(AbstractWidget widget) {
				this.widget = widget;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered,
					float tickDelta) {
				widget.setX(getContentX());
				widget.setY(getContentY());
				widget.setWidth(getContentWidth());
				widget.extractRenderState(context, mouseX, mouseY, tickDelta);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(widget);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(widget);
			}
		}

		static class HeaderEntry extends com.glamardor.absolutecinema.gui.FallbackConfigScreen.OptionList.Entry {
			private final Component text;

			HeaderEntry(Component text) {
				this.text = text;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered,
					float tickDelta) {
				Minecraft client = Minecraft.getInstance();
				context.centeredText(client.font, text,
						getContentXMiddle(), getContentY() + 8, 0xFFE0C070);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of();
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of();
			}
		}
	}
}
