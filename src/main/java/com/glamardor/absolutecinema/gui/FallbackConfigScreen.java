package com.glamardor.absolutecinema.gui;

import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CameraMode;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.config.ColorGrade;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
		super(Text.translatable("absolutecinema.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		list = new OptionList(this.client, this.width, this.height - 96, 40, 25);

		list.addHeader(Text.translatable("absolutecinema.category.general"));
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

		list.addHeader(Text.translatable("absolutecinema.category.filters"));
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

		list.addHeader(Text.translatable("absolutecinema.category.dof"));
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

		list.addHeader(Text.translatable("absolutecinema.category.first_person"));
		list.addWidget(percentSlider("rotation_smoothing", config.rotationSmoothing, 0.0f, 0.95f,
				value -> config.rotationSmoothing = value));
		list.addWidget(percentSlider("position_smoothing", config.positionSmoothing, 0.0f, 0.95f,
				value -> config.positionSmoothing = value));

		list.addHeader(Text.translatable("absolutecinema.category.directed"));
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
		list.addWidget(toggle("include_named", () -> config.includeNamedEntities,
				value -> config.includeNamedEntities = value));

		list.addHeader(Text.translatable("absolutecinema.category.speaker"));
		list.addWidget(secondsSlider("speaker_hold", config.speakerHoldSeconds, 0.5f, 15.0f,
				value -> config.speakerHoldSeconds = value));
		list.addWidget(blocksSlider("speaker_distance", config.speakerMaxDistance, 4.0f, 64.0f,
				value -> config.speakerMaxDistance = value));
		list.addWidget(toggle("rule_of_thirds", () -> config.ruleOfThirds, value -> config.ruleOfThirds = value));

		addDrawableChild(list);

		ButtonWidget reset = ButtonWidget.builder(Text.translatable("absolutecinema.config.reset"), button -> {
			config.resetToDefaults();
			CameraDirector.get().reset();
			// Rebuild so every widget shows the value it was just reset to.
			clearAndInit();
		}).dimensions(this.width / 2 - 154, this.height - 30, 150, 20).build();
		reset.setTooltip(Tooltip.of(Text.translatable("absolutecinema.config.reset.tooltip")));
		addDrawableChild(reset);

		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 + 4, this.height - 30, 150, 20)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
		if (!ConfigScreenFactory.isClothPresent()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("absolutecinema.config.no_cloth"), this.width / 2, this.height - 44, 0xFF9A9A9A);
		}
	}

	@Override
	public void close() {
		config.save();
		CameraDirector.get().reset();
		MinecraftClient.getInstance().setScreen(parent);
	}

	private ClickableWidget toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return cycleButton(key,
				() -> getter.get() ? ScreenTexts.ON : ScreenTexts.OFF,
				() -> setter.accept(!getter.get()));
	}

	private ClickableWidget cycleButton(String key, Supplier<Text> value, Runnable onClick) {
		ButtonWidget button = ButtonWidget.builder(label(key, value.get()), b -> {
			onClick.run();
			b.setMessage(label(key, value.get()));
		}).dimensions(0, 0, ROW_WIDTH, 20).build();
		button.setTooltip(Tooltip.of(Text.translatable("absolutecinema.option." + key + ".tooltip")));
		return button;
	}

	private ClickableWidget percentSlider(String key, float current, float min, float max, Consumer<Float> setter) {
		return new OptionSlider(key, current, min, max, setter,
				value -> Text.literal(Math.round(value * 100.0f) + "%"));
	}

	private ClickableWidget secondsSlider(String key, float current, float min, float max, Consumer<Float> setter) {
		return new OptionSlider(key, current, min, max, setter,
				value -> Text.translatable("absolutecinema.unit.seconds", String.format("%.1f", value)));
	}

	private ClickableWidget blocksSlider(String key, float current, float min, float max, Consumer<Float> setter) {
		return new OptionSlider(key, current, min, max, setter,
				value -> Text.translatable("absolutecinema.unit.blocks", Math.round(value)));
	}

	private static Text label(String key, Text value) {
		return Text.translatable("absolutecinema.option." + key).append(": ").append(value);
	}

	/** Slider over a float range that writes straight back into the config object. */
	private static class OptionSlider extends SliderWidget {
		private final String key;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;
		private final Function<Float, Text> display;

		OptionSlider(String key, float current, float min, float max, Consumer<Float> setter,
				Function<Float, Text> display) {
			super(0, 0, ROW_WIDTH, 20, Text.empty(),
					MathHelper.clamp((current - min) / (max - min), 0.0f, 1.0f));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.display = display;
			setTooltip(Tooltip.of(Text.translatable("absolutecinema.option." + key + ".tooltip")));
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

	private static class OptionList extends ElementListWidget<OptionList.Entry> {
		OptionList(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(ClickableWidget widget) {
			addEntry(new WidgetEntry(widget));
		}

		void addHeader(Text text) {
			addEntry(new HeaderEntry(text));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		abstract static class Entry extends ElementListWidget.Entry<Entry> {
		}

		static class WidgetEntry extends Entry {
			private final ClickableWidget widget;

			WidgetEntry(ClickableWidget widget) {
				this.widget = widget;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				widget.setX(x);
				widget.setY(y);
				widget.setWidth(entryWidth);
				widget.render(context, mouseX, mouseY, tickDelta);
			}

			@Override
			public List<? extends Element> children() {
				return List.of(widget);
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of(widget);
			}
		}

		static class HeaderEntry extends Entry {
			private final Text text;

			HeaderEntry(Text text) {
				this.text = text;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				MinecraftClient client = MinecraftClient.getInstance();
				context.drawCenteredTextWithShadow(client.textRenderer, text,
						x + entryWidth / 2, y + 8, 0xFFE0C070);
			}

			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}
	}
}
