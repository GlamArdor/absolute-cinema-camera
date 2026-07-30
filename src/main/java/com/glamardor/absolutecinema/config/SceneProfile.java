package com.glamardor.absolutecinema.config;

import java.util.ArrayList;
import java.util.List;

/**
 * A named "look" for a scene: which camera mode, which grade, and which filters are on.
 *
 * <p>The point is that nobody wants to open a settings screen in the middle of a scene. You set
 * things up once, save them under a name, and from then on one command or one keypress puts the
 * whole rig into that state.
 *
 * <p>Only the fields worth switching per scene live here. Everything else — smoothing, shot
 * lengths, scene radius — stays global, because those are preferences rather than staging.
 */
public class SceneProfile {
	public String name = "scene";

	public CameraMode mode = CameraMode.DYNAMIC;
	public ColorGrade grade = ColorGrade.NONE;

	public boolean letterbox = true;
	public boolean hideHud = true;
	public boolean hideHand = true;
	public boolean colorGrading = true;
	public boolean depthOfField = true;
	public boolean vignette = true;
	public boolean filmGrain = true;

	public float gradeStrength = 1.0f;
	public float shotDistance = 4.0f;

	public SceneProfile() {
	}

	public SceneProfile(String name) {
		this.name = name;
	}

	/** Reads the current settings into this profile. */
	public SceneProfile capture(CinemaConfig config) {
		mode = config.mode;
		grade = config.colorGrade;
		letterbox = config.letterbox;
		hideHud = config.hideHud;
		hideHand = config.hideHand;
		colorGrading = config.colorGrading;
		depthOfField = config.depthOfField;
		vignette = config.vignette;
		filmGrain = config.filmGrain;
		gradeStrength = config.gradeStrength;
		shotDistance = config.shotDistance;
		return this;
	}

	/** Writes this profile into the live settings. */
	public void apply(CinemaConfig config) {
		config.mode = mode;
		config.colorGrade = grade;
		config.letterbox = letterbox;
		config.hideHud = hideHud;
		config.hideHand = hideHand;
		config.colorGrading = colorGrading;
		config.depthOfField = depthOfField;
		config.vignette = vignette;
		config.filmGrain = filmGrain;
		config.gradeStrength = gradeStrength;
		config.shotDistance = shotDistance;
	}

	/**
	 * The profiles a fresh install starts with — examples of the idea rather than a fixed set,
	 * covering the situations a roleplay server runs into most.
	 */
	public static List<SceneProfile> defaults() {
		List<SceneProfile> list = new ArrayList<>();

		SceneProfile talks = new SceneProfile("talks");
		talks.mode = CameraMode.DIALOGUE;
		talks.grade = ColorGrade.NOBLE_STUDY;
		talks.depthOfField = false;
		list.add(talks);

		SceneProfile ball = new SceneProfile("ball");
		ball.mode = CameraMode.DYNAMIC;
		ball.grade = ColorGrade.GRAND_BALL;
		ball.depthOfField = false;
		ball.shotDistance = 6.0f;
		list.add(ball);

		SceneProfile tavern = new SceneProfile("tavern");
		tavern.mode = CameraMode.SPEAKER_FOCUS;
		tavern.grade = ColorGrade.CAMPFIRE;
		tavern.depthOfField = false;
		list.add(tavern);

		SceneProfile duel = new SceneProfile("duel");
		duel.mode = CameraMode.DYNAMIC;
		duel.grade = ColorGrade.BLOODLUST;
		duel.depthOfField = false;
		duel.shotDistance = 5.0f;
		list.add(duel);

		SceneProfile stage = new SceneProfile("stage");
		stage.mode = CameraMode.TRIPOD;
		stage.grade = ColorGrade.GRAND_BALL;
		stage.depthOfField = false;
		list.add(stage);

		return list;
	}
}
