package com.glamardor.absolutecinema.config;

import net.minecraft.text.Text;

/** How the camera behaves while the cinema mode is on. */
public enum CameraMode {
	/** The player keeps control; the camera only smooths what they do. */
	FIRST_PERSON("first_person"),
	/** The camera detaches and flies pre-composed shots around the player. */
	DYNAMIC("dynamic"),
	/** Like {@link #DYNAMIC}, but the shots follow whoever is talking on Simple Voice Chat. */
	SPEAKER_FOCUS("speaker_focus"),
	/**
	 * Shot-reverse-shot: the classic way a film covers a conversation. Holds the speaker, cuts to
	 * the listener for their reaction, occasionally pulls back to hold both.
	 */
	DIALOGUE("dialogue"),
	/** Locked off where you left it. The camera only turns to follow the scene. */
	TRIPOD("tripod"),
	/** Travels alongside the scene, keeping pace – for walking, processions, rides. */
	SIDE_TRACK("side_track");

	private final String id;

	CameraMode(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public String getTranslationKey() {
		return "absolutecinema.mode." + id;
	}

	public Text getDisplayName() {
		return Text.translatable(getTranslationKey());
	}

	public Text getDescription() {
		return Text.translatable(getTranslationKey() + ".desc");
	}

	/** True when the mod drives the camera itself instead of the player. */
	public boolean isDirected() {
		return this != FIRST_PERSON;
	}

	/**
	 * True when this mode wants to know who is holding the floor – by voice on Simple Voice Chat,
	 * or by writing in chat. The dynamic camera is included but asks the config first: following a
	 * speaker is what separates it from the mode named after doing so.
	 */
	public boolean usesSpeaker() {
		return this == SPEAKER_FOCUS || this == DIALOGUE || this == DYNAMIC;
	}

	public CameraMode next() {
		CameraMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static CameraMode byId(String id) {
		for (CameraMode mode : values()) {
			if (mode.id.equals(id)) {
				return mode;
			}
		}
		return FIRST_PERSON;
	}
}
