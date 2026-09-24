package com.glamardor.absolutecinema.config;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Plain-old-data config, serialised to config/absolutecinema.json.
 *
 * <p>Deliberately field-based with no getters: both the Cloth screen and the fallback screen
 * bind straight to these fields, and Gson round-trips them without any adapters.
 */
public class CinemaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CinemaConfig instance;

	/** Bumped whenever a default changes in a way an existing file must not keep overriding. */
	private static final int CURRENT_VERSION = 4;

	// ---- mode -------------------------------------------------------------------------------
	public CameraMode mode = CameraMode.FIRST_PERSON;
	public ColorGrade colorGrade = ColorGrade.CAMPFIRE;

	// ---- filter toggles ---------------------------------------------------------------------
	public boolean letterbox = true;
	public boolean hideHud = true;
	public boolean hideHand = true;
	/** Hide the chat along with the rest of the HUD. Off: on a roleplay server text is the scene. */
	public boolean hideChat = false;
	public boolean depthOfField = true;
	public boolean colorGrading = true;
	public boolean vignette = true;
	public boolean filmGrain = true;
	public boolean smoothCamera = true;

	// ---- look -------------------------------------------------------------------------------
	/** Height of one black bar as a fraction of the screen. */
	public float letterboxSize = 0.115f;
	/** Seconds the bars take to slide in and out. */
	public float letterboxFadeSeconds = 0.7f;
	/** Strength of the whole grade, 0 = off, 1 = as authored. */
	public float gradeStrength = 1.0f;
	/** Blur strength of the out-of-focus areas. */
	public float dofStrength = 0.35f;
	/** How deep the sharp zone is around the focus point, in blocks. */
	public float dofFocusRange = 6.0f;
	/** Soften what is closer than the focus plane too. Off by default: it ghosts the ground. */
	public boolean dofBlurForeground = false;
	/** How much of the blur the foreground gets when it is enabled. */
	public float dofForegroundAmount = 0.5f;
	/** Fixed focus distance in blocks; ignored when {@link #dofAutoFocus} is on. */
	public float dofFocusDistance = 6.0f;
	/** Focus on whatever the camera is aimed at. */
	public boolean dofAutoFocus = true;

	// ---- first person -----------------------------------------------------------------------
	/** 0 = raw mouse, 1 = very heavy smoothing. */
	public float rotationSmoothing = 0.55f;
	/** Smoothing applied to the camera position (kills head bob and step jitter). */
	public float positionSmoothing = 0.45f;
	/**
	 * Kill the walking sway and the hurt tilt entirely. Vanilla rocks the view side to side on
	 * every step, which no camera on a real set does; smoothing the position cannot remove it
	 * because the sway is applied to the view matrix, not to the camera.
	 */
	public boolean stabilizeCamera = true;

	// ---- directed camera --------------------------------------------------------------------
	/** Average length of one shot, in seconds. */
	public float shotDuration = 9.0f;
	/** How far the camera likes to sit from its subject, in blocks. */
	public float shotDistance = 4.0f;
	/** Overall speed multiplier of camera moves. */
	public float shotSpeed = 1.0f;
	/** Cut hard between shots instead of gliding across. */
	public boolean hardCuts = false;
	/** Seconds a cross-fade move between two shots takes (ignored with hard cuts). */
	public float transitionSeconds = 1.4f;
	/** Add a very small hand-held drift so shots never look frozen. */
	public boolean handheldDrift = true;
	/** Keep the camera from clipping into blocks. */
	public boolean avoidWalls = true;
	/** How far around you people are still counted as part of the scene, in blocks. */
	public float sceneRadius = 14.0f;
	/**
	 * Count named creatures (that is how NPCs are usually marked) as part of the scene. Off by
	 * default: on a busy server a named horse or pet would otherwise pull shots off the people.
	 */
	public boolean includeNamedEntities = false;
	/**
	 * Back the camera off until every participant is inside the frame. Without it a shot is only
	 * as wide as the group's radius suggests, and people standing off to one side fall out of
	 * view — worse in a room, where the walls cap how far back the camera may sit.
	 */
	public boolean keepEveryoneInFrame = true;
	/** Hide signs, frames and other clutter that ends up right in front of the lens. */
	public boolean hideNearbyBlockers = true;
	/** How close something has to be to the lens before it is hidden, in blocks. */
	public float blockerDistance = 1.8f;

	/**
	 * Fade out people who are not in the scene but have wandered in front of the lens.
	 *
	 * <p>The camera is pushed out of a body it is standing inside, and it recomposes when the
	 * scene is hidden for long enough, but neither answers a passer-by who stops half a metre to
	 * the side of the lens: nothing is blocked badly enough to be worth a cut, and the frame is
	 * still a shoulder. Cutting for every passer-by on a busy server would be worse than the
	 * shoulder, so they are faded instead, and only while they are that close.
	 *
	 * <p>Participants are never touched, however close they get. Someone in the scene standing in
	 * front of the camera is a foreground, not a fault.
	 */
	public boolean fadeNearbyPlayers = true;
	/** How close to the lens somebody has to be before they start fading, in blocks. */
	public float playerFadeDistance = 2.5f;

	/**
	 * How far above or below you somebody may be and still count as part of the scene, in blocks.
	 * The scene radius alone is a sphere, and a sphere in a tavern reaches through the ceiling: the
	 * table upstairs joins your conversation and the camera pulls back to hold a building. 0 removes
	 * the limit and gives the plain sphere back.
	 *
	 * <p>Three blocks by default, which is one storey and nothing more. Four was a storey plus the
	 * slack to reach the next one: standing on a first floor, or on any step above the street, it
	 * let the ground floor back into the scene, and the camera went after a conversation down there
	 * instead of holding the one in the room. A scene worth filming stands on one floor.
	 */
	public float sceneHeightLimit = 3.0f;

	/**
	 * Whether two people stand on close enough levels to belong to the same scene.
	 *
	 * <p>Lives here rather than in the director because the height limit is not only about framing.
	 * Everything that lets somebody claim the camera — the scene, a voice, a line of chat — has to
	 * ask the same question, or the limit holds the floor above out of the shot while still letting
	 * it cut the camera away.
	 */
	public boolean sameLevel(double selfY, double otherY) {
		return sceneHeightLimit <= 0.0f || Math.abs(otherY - selfY) <= sceneHeightLimit;
	}

	/**
	 * Raises or lowers every directed frame, in blocks. Emote mods seat and lie people down without
	 * telling the game, so their eyes stay where a standing player's would be and the camera aims
	 * at empty air above their head. Nothing in the client can see through that, so this is the
	 * handle for it.
	 */
	public float cameraHeight;

	/**
	 * How far the player may drift from everybody else before the camera stops filming the group
	 * and comes with them, in blocks. The camera belongs to the person who turned it on: walking
	 * away from a scene should not leave them off screen watching a conversation they have left.
	 * 0 keeps the camera with the group however far they wander.
	 */
	public float leaveSceneDistance = 10.0f;

	// ---- speaker focus ----------------------------------------------------------------------
	/** Seconds of silence before the camera lets go of a speaker and returns to the group. */
	public float speakerHoldSeconds = 0.6f;
	/**
	 * How briefly the current speaker has to pause before somebody else may take the frame.
	 * This is what makes short exchanges work: hold is about silence, handover is about a reply.
	 */
	public float speakerHandoverSeconds = 0.25f;
	/** Shortest a cut may last, so people talking over each other cannot make the camera stutter. */
	public float minShotSeconds = 0.6f;
	/**
	 * Longest one person may hold the frame before the camera goes back to the room, in seconds.
	 * Somebody holding push-to-talk down, or running open-mic, would otherwise own the shot for
	 * as long as they felt like it. 0 turns the limit off.
	 */
	public float maxSpeakerFocusSeconds = 20.0f;
	/** How long the camera stays off a speaker who has run out their turn, in seconds. */
	public float speakerBreakSeconds = 7.0f;
	/** Ignore speakers further away than this, in blocks. */
	public float speakerMaxDistance = 24.0f;
	/** Frame the speaker off-centre, the way a real shot would. */
	public boolean ruleOfThirds = true;

	/**
	 * Hold a frame until asked for another one, instead of changing it when its time is up.
	 *
	 * <p>Only applies while the camera is filming the room. The moment somebody has the floor the
	 * camera cuts to them as usual — being told who is speaking is the whole point of those modes,
	 * and a manual key cannot be faster than that. What this switches off is the part that happens
	 * on a timer: the change of angle every eight seconds or so, chosen at random.
	 *
	 * <p>A shot jammed against a wall, or aimed at the back of a pillar, is still recomposed
	 * without asking. Holding a frame is worth doing; holding a broken one is not.
	 */
	public boolean manualShotChanges;
	/**
	 * Let the dynamic camera follow whoever is speaking or writing, the same way the speaker focus
	 * mode does. Turn it off to keep the dynamic camera on the scene as a whole, and the approach
	 * to a face reserved for the two modes named after it.
	 */
	public boolean dynamicFollowsSpeaker = true;

	// ---- roleplay in text ---------------------------------------------------------------------
	/**
	 * Treat a chat message or a roleplay emote as a turn, the same as speech. On a roleplay server
	 * half the scene is played out in text, and a camera deaf to it films the wrong person.
	 */
	public boolean reactToChat = true;
	/** React to your own messages as well. You are a participant in the scene, not just its operator. */
	public boolean reactToOwnChat = true;
	/** How long a message holds the frame before its length is counted, in seconds. */
	public float chatHoldSeconds = 0.8f;
	/** Extra seconds per hundred characters — roughly how long the message takes to read. */
	public float chatSecondsPer100 = 5.0f;
	/** However long the message, the frame is never held past this, in seconds. */
	public float chatMaxSeconds = 8.0f;
	/**
	 * Messages containing any of these are ignored — out-of-character asides, a global channel,
	 * whatever a given server uses. Matched anywhere in the line, because the server's own prefix
	 * comes first and the marker sits after it. Empty by default: nothing is guessed for you.
	 */
	public List<String> chatIgnore = new ArrayList<>(List.of("((*))"));

	// ---- misc -------------------------------------------------------------------------------
	/** Show a small toast/action bar note when the mode changes. */
	public boolean announceToggle = true;
	/** Drop out of cinema mode the moment we take damage — nobody ambushes you mid-scene. */
	public boolean exitOnDamage = true;

	// ---- scene profiles ---------------------------------------------------------------------
	/** Saved staging presets, switched with one command or key. */
	public List<SceneProfile> profiles = SceneProfile.defaults();
	/** Name of the profile applied last, so the cycle key knows where it is. */
	public String activeProfile = "";

	/**
	 * Which generation of defaults this file was written by. Gson keeps whatever a stored file
	 * says, so a timing whose default changes would go on behaving the old way for everybody who
	 * already has a config — silently, and looking exactly like the fix never landed.
	 *
	 * <p>It starts at zero and nowhere else. Gson runs field initialisers and only overwrites what
	 * the json actually contains, so a field defaulting to the current version would read as
	 * up-to-date in every file written before the field existed — which is precisely the set of
	 * files that need migrating.
	 */
	public int configVersion;

	public static CinemaConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(AbsoluteCinema.MOD_ID + ".json");
	}

	private static CinemaConfig load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				CinemaConfig loaded = GSON.fromJson(reader, CinemaConfig.class);
				if (loaded != null) {
					loaded.migrate();
					loaded.clamp();
					return loaded;
				}
			} catch (Exception e) {
				AbsoluteCinema.LOGGER.warn("Could not read {}, falling back to defaults", path, e);
			}
		}
		CinemaConfig fresh = new CinemaConfig();
		// Written by this build, so it never asks to be migrated to itself.
		fresh.configVersion = CURRENT_VERSION;
		fresh.save();
		return fresh;
	}

	public void save() {
		clamp();
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			AbsoluteCinema.LOGGER.error("Could not write {}", path, e);
		}
	}

	/**
	 * Puts every setting back to what a fresh install would have — except the saved scene
	 * profiles, which are the player's own work and would be painful to lose to a misclick.
	 *
	 * <p>Reflection over the public fields rather than three dozen assignments: adding a new
	 * option should not mean remembering to update a reset method as well.
	 */
	public void resetToDefaults() {
		CinemaConfig fresh = new CinemaConfig();
		for (Field field : CinemaConfig.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			String name = field.getName();
			// The version is not a setting: resetting it to zero would ask this very file to be
			// migrated again on the next start.
			if (name.equals("profiles") || name.equals("activeProfile") || name.equals("configVersion")) {
				continue;
			}
			try {
				field.set(this, field.get(fresh));
			} catch (IllegalAccessException e) {
				AbsoluteCinema.LOGGER.warn("Could not reset {}", name, e);
			}
		}
		configVersion = CURRENT_VERSION;
		save();
	}

	// ---- scene profile helpers --------------------------------------------------------------

	@Nullable
	public SceneProfile findProfile(String name) {
		for (SceneProfile profile : profiles) {
			if (profile.name.equalsIgnoreCase(name)) {
				return profile;
			}
		}
		return null;
	}

	/** Applies a profile by name, remembering it as the active one. Returns false if unknown. */
	public boolean applyProfile(String name) {
		SceneProfile profile = findProfile(name);
		if (profile == null) {
			return false;
		}
		profile.apply(this);
		activeProfile = profile.name;
		save();
		return true;
	}

	/** Saves the current settings under a name, replacing any profile already using it. */
	public SceneProfile saveProfile(String name) {
		String cleaned = name.toLowerCase(Locale.ROOT);
		SceneProfile existing = findProfile(cleaned);
		if (existing == null) {
			existing = new SceneProfile(cleaned);
			profiles.add(existing);
		}
		existing.capture(this);
		activeProfile = existing.name;
		save();
		return existing;
	}

	public boolean deleteProfile(String name) {
		SceneProfile profile = findProfile(name);
		if (profile == null) {
			return false;
		}
		profiles.remove(profile);
		if (activeProfile.equalsIgnoreCase(name)) {
			activeProfile = "";
		}
		save();
		return true;
	}

	/** The next profile after the active one, wrapping around. Null when there are none. */
	@Nullable
	public SceneProfile nextProfile() {
		if (profiles.isEmpty()) {
			return null;
		}
		int index = -1;
		for (int i = 0; i < profiles.size(); i++) {
			if (profiles.get(i).name.equalsIgnoreCase(activeProfile)) {
				index = i;
				break;
			}
		}
		return profiles.get((index + 1) % profiles.size());
	}

	/**
	 * Brings a file written by an older build up to date.
	 *
	 * <p>Only the settings whose <em>meaning</em> changed are touched, and only once. The speaker
	 * timings are the case in point: the first playtest showed the camera was far too slow to
	 * follow a conversation, and the fix was mostly a matter of shorter defaults — which would
	 * have reached nobody who already had a config, because a stored value always wins.
	 */
	private void migrate() {
		if (configVersion >= CURRENT_VERSION) {
			configVersion = CURRENT_VERSION;
			return;
		}
		CinemaConfig fresh = new CinemaConfig();
		speakerHoldSeconds = fresh.speakerHoldSeconds;
		speakerHandoverSeconds = fresh.speakerHandoverSeconds;
		minShotSeconds = fresh.minShotSeconds;
		maxSpeakerFocusSeconds = fresh.maxSpeakerFocusSeconds;
		speakerBreakSeconds = fresh.speakerBreakSeconds;
		chatHoldSeconds = fresh.chatHoldSeconds;
		chatSecondsPer100 = fresh.chatSecondsPer100;
		chatMaxSeconds = fresh.chatMaxSeconds;
		if (chatIgnore == null || chatIgnore.isEmpty()) {
			// Nobody had set anything here, so they get what a fresh install now gets: asides in
			// double brackets are commentary, not part of the scene.
			chatIgnore = new ArrayList<>(fresh.chatIgnore);
		}
		AbsoluteCinema.LOGGER.info("config updated to version {}: the speaker and message timings "
				+ "were reset to the new defaults", CURRENT_VERSION);
		configVersion = CURRENT_VERSION;
		save();
	}

	/** Keeps hand-edited json from producing a camera that flies to the moon. */
	public void clamp() {
		if (mode == null) {
			mode = CameraMode.FIRST_PERSON;
		}
		if (colorGrade == null) {
			colorGrade = ColorGrade.NONE;
		}
		if (profiles == null) {
			profiles = SceneProfile.defaults();
		}
		profiles = new ArrayList<>(profiles);
		profiles.removeIf(profile -> profile == null || profile.name == null || profile.name.isBlank());
		if (activeProfile == null) {
			activeProfile = "";
		}
		letterboxSize = clamp(letterboxSize, 0.0f, 0.35f);
		letterboxFadeSeconds = clamp(letterboxFadeSeconds, 0.0f, 4.0f);
		gradeStrength = clamp(gradeStrength, 0.0f, 1.0f);
		dofStrength = clamp(dofStrength, 0.0f, 1.0f);
		dofFocusRange = clamp(dofFocusRange, 0.5f, 32.0f);
		dofForegroundAmount = clamp(dofForegroundAmount, 0.0f, 1.0f);
		dofFocusDistance = clamp(dofFocusDistance, 0.5f, 64.0f);
		rotationSmoothing = clamp(rotationSmoothing, 0.0f, 0.95f);
		positionSmoothing = clamp(positionSmoothing, 0.0f, 0.95f);
		shotDuration = clamp(shotDuration, 2.0f, 60.0f);
		shotDistance = clamp(shotDistance, 1.0f, 16.0f);
		shotSpeed = clamp(shotSpeed, 0.2f, 3.0f);
		transitionSeconds = clamp(transitionSeconds, 0.2f, 5.0f);
		sceneRadius = clamp(sceneRadius, 3.0f, 48.0f);
		leaveSceneDistance = clamp(leaveSceneDistance, 0.0f, 48.0f);
		sceneHeightLimit = clamp(sceneHeightLimit, 0.0f, 32.0f);
		cameraHeight = clamp(cameraHeight, -2.0f, 2.0f);
		blockerDistance = clamp(blockerDistance, 0.5f, 5.0f);
		playerFadeDistance = clamp(playerFadeDistance, 0.5f, 8.0f);
		speakerHoldSeconds = clamp(speakerHoldSeconds, 0.2f, 15.0f);
		speakerHandoverSeconds = clamp(speakerHandoverSeconds, 0.0f, 3.0f);
		minShotSeconds = clamp(minShotSeconds, 0.0f, 5.0f);
		maxSpeakerFocusSeconds = clamp(maxSpeakerFocusSeconds, 0.0f, 120.0f);
		speakerBreakSeconds = clamp(speakerBreakSeconds, 1.0f, 60.0f);
		speakerMaxDistance = clamp(speakerMaxDistance, 4.0f, 64.0f);
		chatHoldSeconds = clamp(chatHoldSeconds, 0.0f, 20.0f);
		chatSecondsPer100 = clamp(chatSecondsPer100, 0.0f, 30.0f);
		chatMaxSeconds = clamp(chatMaxSeconds, 0.5f, 60.0f);
		if (chatIgnore == null) {
			chatIgnore = new ArrayList<>();
		}
		chatIgnore = new ArrayList<>(chatIgnore);
		chatIgnore.removeIf(entry -> entry == null || entry.isBlank());
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
