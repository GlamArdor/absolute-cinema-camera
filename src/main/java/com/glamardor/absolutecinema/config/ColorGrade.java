package com.glamardor.absolutecinema.config;

import net.minecraft.text.Text;

/**
 * A colour grading preset. The numbers are fed straight into the post effect uniform block,
 * so keep them in the ranges the shader expects.
 *
 * <p>lift/gamma/gain are the classic three-way colour corrector: lift tints the shadows,
 * gain tints the highlights, gamma bends the midtones.
 */
public enum ColorGrade {
	/**
	 * Order matters: this is the order the settings screen lists them in and the order the
	 * cycle key walks through, so the presets are grouped by the kind of look they give –
	 * bright, then distorted, then colour-led, then dark, then stylised stock.
	 */
	NONE("none",
			new float[] { 0.0f, 0.0f, 0.0f },
			new float[] { 1.0f, 1.0f, 1.0f },
			new float[] { 1.0f, 1.0f, 1.0f },
			1.0f, 1.0f, 0.0f, 0.0f),

	// ---- bright ---------------------------------------------------------------------------

	/** Low sun: amber highlights, long soft contrast, and light hazing off every surface. */
	GOLDEN_HOUR("golden_hour",
			new float[] { 0.040f, 0.026f, 0.006f },
			new float[] { 1.00f, 1.00f, 1.02f },
			new float[] { 1.16f, 1.03f, 0.82f },
			1.10f, 0.95f, 0.30f, 0.015f,
			1.35f, 0.0f, 0.0f),

	/**
	 * A study lit by candles: gold on the faces, warm lifted shadows, and a visible breath in
	 * the light. Deliberately not a dark room – the vignette is only a hint.
	 */
	NOBLE_STUDY("noble_study",
			new float[] { 0.052f, 0.022f, 0.000f },
			new float[] { 0.98f, 1.00f, 1.05f },
			new float[] { 1.18f, 1.04f, 0.82f },
			1.08f, 1.10f, 0.15f, 0.02f,
			1.35f, 0.0f, 0.100f),

	/** A chandeliered hall: bright lifted gold, airy contrast, light blooming everywhere. */
	GRAND_BALL("grand_ball",
			new float[] { 0.048f, 0.034f, 0.020f },
			new float[] { 0.99f, 1.00f, 1.02f },
			new float[] { 1.17f, 1.08f, 0.90f },
			1.12f, 1.03f, 0.18f, 0.0f,
			1.40f, 0.0f, 0.0f),

	/** Warm firelight, deep cool shadows, gentle film grain. */
	CAMPFIRE("campfire",
			new float[] { 0.030f, 0.008f, -0.012f },
			new float[] { 0.98f, 1.00f, 1.06f },
			new float[] { 1.12f, 1.00f, 0.84f },
			1.05f, 1.12f, 0.35f, 0.035f),

	/** Deep winter: pale, cold, flat, the light bouncing off snow. */
	FROST("frost",
			new float[] { 0.040f, 0.048f, 0.058f },
			new float[] { 1.00f, 1.00f, 0.98f },
			new float[] { 0.98f, 1.04f, 1.12f },
			0.70f, 0.90f, 0.25f, 0.015f,
			0.60f, 0.0f, 0.0f),

	// ---- distorted ------------------------------------------------------------------------

	/**
	 * Too much wine: the room sways, edges split into a second faint copy of themselves, and
	 * every light smears. Warm, soft, and slightly out of control.
	 */
	DRUNK("drunk",
			new float[] { 0.034f, 0.020f, 0.004f },
			new float[] { 1.00f, 1.00f, 1.01f },
			new float[] { 1.12f, 1.03f, 0.91f },
			1.16f, 0.92f, 0.55f, 0.02f,
			0.90f, 0.0055f, 0.030f, 0.0115f, 0.50f),

	/**
	 * Fever dream: sickly yellow-green light over bruised magenta shadows, the picture breathing
	 * and swimming, colour splitting at the edges.
	 */
	FEVER("fever",
			new float[] { 0.032f, -0.018f, 0.038f },
			new float[] { 0.94f, 1.02f, 0.96f },
			new float[] { 1.12f, 1.08f, 0.84f },
			1.38f, 1.22f, 0.68f, 0.09f,
			0.60f, 0.011f, 0.070f, 0.0050f),

	// ---- colour led -----------------------------------------------------------------------

	/** Unmistakably pink: rose shadows, magenta-leaning highlights, a glow on everything bright. */
	ROMANCE("romance",
			new float[] { 0.085f, 0.030f, 0.062f },
			new float[] { 0.95f, 1.03f, 0.98f },
			new float[] { 1.16f, 0.94f, 1.07f },
			1.15f, 0.90f, 0.45f, 0.0f,
			0.60f, 0.0f, 0.0f),

	/** Everything drowns in red: hot highlights, red-bled midtones, crushed cold shadows. */
	BLOODLUST("bloodlust",
			new float[] { -0.018f, -0.038f, -0.030f },
			new float[] { 1.14f, 0.92f, 0.90f },
			new float[] { 1.34f, 0.80f, 0.78f },
			0.90f, 1.38f, 0.70f, 0.05f,
			0.22f, 0.0f, 0.0f),

	/**
	 * Something is being worked. Violet shadows against cyan light – the teal-and-magenta split
	 * that reads as "not natural light" – pushed saturation, a hard glow, and the picture
	 * fringing and pulsing at the edges.
	 */
	ENCHANTMENT("enchantment",
			new float[] { 0.030f, -0.010f, 0.055f },
			new float[] { 0.96f, 1.02f, 0.94f },
			new float[] { 0.80f, 1.10f, 1.30f },
			1.45f, 1.15f, 0.45f, 0.020f,
			1.30f, 0.0060f, 0.045f),

	// ---- dark and unpleasant --------------------------------------------------------------

	/** Flat, cold, slightly milky – a grey morning through a window. */
	OVERCAST_MORNING("overcast_morning",
			new float[] { 0.045f, 0.052f, 0.062f },
			new float[] { 1.02f, 1.01f, 0.99f },
			new float[] { 0.94f, 0.98f, 1.06f },
			0.78f, 0.88f, 0.22f, 0.02f),

	/** Driving rain: cold grey-blue, hard contrast, everything wet and glinting. */
	STORM("storm",
			new float[] { -0.005f, 0.005f, 0.020f },
			new float[] { 1.02f, 1.01f, 0.98f },
			new float[] { 0.88f, 0.94f, 1.06f },
			0.72f, 1.30f, 0.45f, 0.030f,
			0.55f, 0.0f, 0.0f),

	/** Night shot the way film does it: blue, dim, desaturated, quiet. */
	MOONLIGHT("moonlight",
			new float[] { -0.010f, 0.004f, 0.030f },
			new float[] { 1.05f, 1.02f, 0.94f },
			new float[] { 0.80f, 0.90f, 1.14f },
			0.65f, 1.12f, 0.50f, 0.03f,
			0.22f, 0.0f, 0.0f),

	/**
	 * Plotting after dark: the room sunk in blue-green, one warm candle holding the faces.
	 * Saturation stays above 1 on purpose – pulling it down was what made this look colourless.
	 */
	CONSPIRACY("conspiracy",
			new float[] { -0.030f, 0.020f, 0.026f },
			new float[] { 1.08f, 0.98f, 1.00f },
			new float[] { 1.10f, 1.04f, 0.82f },
			1.10f, 1.22f, 0.68f, 0.030f,
			0.50f, 0.0f, 0.030f),

	/** One harsh lamp: bleached colour, brutal contrast, everything else in the dark. */
	INTERROGATION("interrogation",
			new float[] { -0.025f, -0.025f, -0.020f },
			new float[] { 1.08f, 1.06f, 1.00f },
			new float[] { 1.06f, 1.02f, 0.94f },
			0.45f, 1.45f, 0.85f, 0.04f,
			0.0f, 0.0f, 0.0f),

	/** Sickly green-magenta, crushed and grainy. */
	BAD_DREAM("bad_dream",
			new float[] { -0.020f, 0.010f, -0.015f },
			new float[] { 1.06f, 0.94f, 1.08f },
			new float[] { 0.92f, 1.06f, 0.90f },
			0.62f, 1.30f, 0.62f, 0.06f),

	// ---- stylised stock -------------------------------------------------------------------

	/** Something remembered: soft sepia, lifted, glowing at the edges. */
	MEMORY("memory",
			new float[] { 0.060f, 0.048f, 0.026f },
			new float[] { 0.98f, 1.00f, 1.04f },
			new float[] { 1.10f, 1.04f, 0.88f },
			0.55f, 0.88f, 0.50f, 0.0f,
			0.95f, 0.0f, 0.0f),

	/** Scratched sepia stock: heavy grain, gate flicker, hard vignette. */
	OLD_FILM("old_film",
			new float[] { 0.045f, 0.030f, 0.008f },
			new float[] { 1.02f, 1.00f, 1.04f },
			new float[] { 1.14f, 1.02f, 0.78f },
			0.18f, 1.25f, 0.75f, 0.14f,
			0.10f, 0.0f, 0.050f),

	/** Black and white with a hint of warmth in the highlights. */
	MONOCHROME("monochrome",
			new float[] { 0.010f, 0.008f, 0.004f },
			new float[] { 1.00f, 1.00f, 1.00f },
			new float[] { 1.04f, 1.00f, 0.96f },
			0.0f, 1.18f, 0.40f, 0.05f);

	private final String id;
	private final float[] lift;
	private final float[] gamma;
	private final float[] gain;
	private final float saturation;
	private final float contrast;
	private final float vignette;
	private final float grain;
	/** Glow bled out of the highlights – halation, in film terms. */
	private final float bloom;
	/** Radial split of the red and blue channels, in screen widths. */
	private final float aberration;
	/** Amount the exposure wobbles, the way a film gate does. */
	private final float flicker;
	/** How far the whole image swims, in screen widths. */
	private final float warp;
	/** Strength of a second, offset copy of the picture – seeing double. */
	private final float doubleVision;

	ColorGrade(String id, float[] lift, float[] gamma, float[] gain,
			float saturation, float contrast, float vignette, float grain) {
		this(id, lift, gamma, gain, saturation, contrast, vignette, grain, 0.0f, 0.0f, 0.0f, 0.0f);
	}

	ColorGrade(String id, float[] lift, float[] gamma, float[] gain,
			float saturation, float contrast, float vignette, float grain,
			float bloom, float aberration, float flicker) {
		this(id, lift, gamma, gain, saturation, contrast, vignette, grain, bloom, aberration,
				flicker, 0.0f);
	}

	ColorGrade(String id, float[] lift, float[] gamma, float[] gain,
			float saturation, float contrast, float vignette, float grain,
			float bloom, float aberration, float flicker, float warp) {
		this(id, lift, gamma, gain, saturation, contrast, vignette, grain, bloom, aberration,
				flicker, warp, 0.0f);
	}

	ColorGrade(String id, float[] lift, float[] gamma, float[] gain,
			float saturation, float contrast, float vignette, float grain,
			float bloom, float aberration, float flicker, float warp, float doubleVision) {
		this.id = id;
		this.lift = lift;
		this.gamma = gamma;
		this.gain = gain;
		this.saturation = saturation;
		this.contrast = contrast;
		this.vignette = vignette;
		this.grain = grain;
		this.bloom = bloom;
		this.aberration = aberration;
		this.flicker = flicker;
		this.warp = warp;
		this.doubleVision = doubleVision;
	}

	public String getId() {
		return id;
	}

	public float[] getLift() {
		return lift;
	}

	public float[] getGamma() {
		return gamma;
	}

	public float[] getGain() {
		return gain;
	}

	public float getSaturation() {
		return saturation;
	}

	public float getContrast() {
		return contrast;
	}

	public float getVignette() {
		return vignette;
	}

	public float getGrain() {
		return grain;
	}

	public float getBloom() {
		return bloom;
	}

	public float getAberration() {
		return aberration;
	}

	public float getFlicker() {
		return flicker;
	}

	public float getWarp() {
		return warp;
	}

	public float getDoubleVision() {
		return doubleVision;
	}

	/**
	 * Whether depth of field suits this look at all.
	 *
	 * <p>Softening the background reads as dreamlike or intimate, which fits an altered state or
	 * a close conversation – and fights everything a bright, crisp room shot is trying to do. So
	 * the bright and stylised presets keep the whole frame sharp even with the filter switched on.
	 */
	public boolean allowsDepthOfField() {
		return switch (this) {
			case DRUNK, FEVER, ROMANCE, BLOODLUST, OVERCAST_MORNING, MOONLIGHT, BAD_DREAM,
					MEMORY, ENCHANTMENT, CONSPIRACY -> true;
			default -> false;
		};
	}

	public String getTranslationKey() {
		return "absolutecinema.grade." + id;
	}

	public Text getDisplayName() {
		return Text.translatable(getTranslationKey());
	}

	public ColorGrade next() {
		ColorGrade[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static ColorGrade byId(String id) {
		for (ColorGrade grade : values()) {
			if (grade.id.equals(id)) {
				return grade;
			}
		}
		return NONE;
	}
}
