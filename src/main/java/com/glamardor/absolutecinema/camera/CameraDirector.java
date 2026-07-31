package com.glamardor.absolutecinema.camera;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CameraMode;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.voice.SpeakerTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Picks and plays back camera moves for the directed modes.
 *
 * <p>The camera films a <em>scene</em>, not a player: everyone standing together is collected
 * into a group, and shots are composed around that group at a distance wide enough to hold all
 * of it. Only one thing ever pulls the camera in close — someone actually speaking, in the
 * speaker focus mode.
 *
 * <p>Shots are described relative to the group (an angle around it, a distance factor, a height)
 * and evaluated per rendered frame in real time, so the motion stays smooth at any framerate.
 */
public final class CameraDirector {
	private static final CameraDirector INSTANCE = new CameraDirector();

	private static final double DEG = Math.PI / 180.0;
	private static final boolean DEBUG = Boolean.getBoolean("absolutecinema.debug");

	/** Distances used for the intimate shots, in blocks. Everything else scales with the group. */
	private static final double FACE_DISTANCE_MIN = 1.35;
	private static final double FACE_DISTANCE_MAX = 2.30;

	/** Furthest the room probe looks; also the "we are outdoors" answer. */
	private static final double MAX_ROOM_RADIUS = 16.0;

	/** A shot squeezed to less than this share of its distance has nowhere to go — recompose. */
	private static final double CRAMPED_RATIO = 0.45;

	/** Below this average clear radius the room is treated as too small for travelling shots. */
	private static final double TIGHT_ROOM_RADIUS = 3.2;

	/** Closer than this to the subject and the camera is inside them — abandon the shot fast. */
	private static final double PERSONAL_SPACE = 1.15;

	/** How far around the front of the scene shots may swing, in degrees either way. */
	private static final double FRONT_ARC = 125.0;

	/** Side track: how far round from the scene's facing the camera runs, and how often it swaps. */
	private static final double SIDE_ANGLE = 82.0;
	private static final float SIDE_SWAP_SECONDS = 22.0f;

	/** Past this much movement in one frame we assume a teleport and stop interpolating. */
	private static final double TELEPORT_DISTANCE = 16.0;

	/**
	 * How the frame follows the way people are facing.
	 *
	 * <p>A shot is built around an angle, and that angle used to be read off the subject's own
	 * yaw every frame — so the person being filmed was steering the camera with their mouse, for
	 * everybody watching. Now the angle belongs to the shot: it is fixed when the shot is
	 * composed, ignores anything smaller than the dead zone, and past that turns no faster than
	 * the rate below. Someone glancing around no longer moves the camera at all; someone turning
	 * right round is followed slowly, like an operator would.
	 */
	private static final double FACING_DEAD_ZONE = 35.0 * Math.PI / 180.0;
	private static final double FACING_FOLLOW_RATE = 11.0 * Math.PI / 180.0;

	/** Leave this much of the frame empty around the outermost person. */
	private static final double FRAME_MARGIN = 0.86;

	/** Half the width of a person, for the fit test. */
	private static final double BODY_HALF_WIDTH = 0.42;

	/** Furthest the fit test will pull the camera back before giving up and recomposing. */
	private static final double MAX_FIT_PULLBACK = 9.0;

	private final Random random = new Random();

	@Nullable
	private Shot shot;
	private float shotElapsed;
	private ShotType lastType;

	/** Blend state used when gliding from the previous shot into the new one. */
	private Vec3d blendFromPos = Vec3d.ZERO;
	private Vec3d blendFromLook = Vec3d.ZERO;
	private float blendElapsed;
	private float blendDuration;

	private Vec3d pos = Vec3d.ZERO;
	private Vec3d look = Vec3d.ZERO;
	private boolean hasPose;

	@Nullable
	private UUID lastSpeaker;
	/** The speaker the camera is currently committed to, and when it committed. */
	@Nullable
	private Entity heldSpeaker;
	private float lastSpeakerCut = -100.0f;
	/** True while the current shot is framing a speaker rather than the whole group. */
	private boolean framingSpeaker;
	private boolean openOnFace;
	private float clock;

	/** Cached result of the room probe, and how long the current shot has been up against a wall. */
	private double lastRoomRadius;
	private float lastRoomProbe = -10.0f;
	private float crampedFor;

	/** How long the current shot has had most of the scene hidden behind something. */
	private float blindFor;
	private float lastVisibilityProbe = -10.0f;

	/** Clear distance in each of the eight probe directions — lets shots pick the open side. */
	private final double[] dirClearances = new double[8];

	/** Set when the wall clamp had to bring the camera inside the subject's personal space. */
	private boolean severelyCramped;

	/** Where the wall rays start this frame: the subject's eye point, before any framing shift. */
	@Nullable
	private Vec3d clampOrigin;

	/** Tripod mode: the spot the camera was planted on. */
	@Nullable
	private Vec3d tripodPos;

	/** Side track mode: which side of the scene we are running along, and when it last changed. */
	private double trackSide = 1.0;
	private float lastSideSwap = -100.0f;
	/** Slow-following copy of the scene's facing, used by the modes that have no shot to hold it. */
	private double trackedFacing;
	private boolean hasTrackedFacing;

	/** Dialogue mode state. */
	private DialoguePhase dialoguePhase = DialoguePhase.TWO_SHOT;
	private float dialoguePhaseLeft;
	private int dialogueCuts;
	private double dialogueSide = 1.0;
	private double dialogueDistance = 1.9;
	@Nullable
	private Entity dialogueFirst;
	@Nullable
	private Entity dialogueSecond;

	private enum DialoguePhase {
		ON_SPEAKER,
		ON_LISTENER,
		TWO_SHOT
	}

	private CameraDirector() {
	}

	public static CameraDirector get() {
		return INSTANCE;
	}

	public void reset() {
		shot = null;
		hasPose = false;
		lastSpeaker = null;
		heldSpeaker = null;
		lastSpeakerCut = -100.0f;
		framingSpeaker = false;
		openOnFace = false;
		blendElapsed = 0.0f;
		blendDuration = 0.0f;
		clampOrigin = null;
		crampedFor = 0.0f;
		blindFor = 0.0f;
		tripodPos = null;
		lastSideSwap = -100.0f;
		hasTrackedFacing = false;
		dialogueFirst = null;
		dialogueSecond = null;
		dialoguePhaseLeft = 0.0f;
		dialogueCuts = 0;
	}

	public Vec3d getPos() {
		return pos;
	}

	/** The point the current shot is aimed at — also what the depth of field focuses on. */
	public Vec3d getLook() {
		return look;
	}

	public float getYaw() {
		Vec3d dir = look.subtract(pos);
		if (dir.lengthSquared() < 1.0E-6) {
			return 0.0f;
		}
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	public float getPitch() {
		Vec3d dir = look.subtract(pos);
		double length = dir.length();
		if (length < 1.0E-4) {
			return 0.0f;
		}
		return (float) Math.toDegrees(Math.asin(MathHelper.clamp(-dir.y / length, -1.0, 1.0)));
	}

	/** Runs one frame of direction. Returns false when there is nothing to film. */
	public boolean update(MinecraftClient client, float dt, float tickProgress) {
		if (client.world == null || client.player == null) {
			return false;
		}

		CinemaConfig config = CinemaConfig.get();

		UUID speakerId = config.mode.usesVoiceChat()
				? SpeakerTracker.getCurrentSpeaker(config.speakerHoldSeconds, config.speakerHandoverSeconds,
						config.maxSpeakerFocusSeconds, config.speakerBreakSeconds)
				: null;
		Entity candidate = speakerId == null ? null : findSpeaker(client, config, speakerId);

		// Anti-chatter: when people talk over each other the tracker can flip between them several
		// times a second, and a camera that follows every flip is unwatchable. A cut has to stand
		// for at least minShotSeconds before the next one is allowed; whoever holds the floor when
		// that time is up gets the frame.
		boolean speakerChanged = false;
		UUID candidateId = candidate == null ? null : candidate.getUuid();
		boolean heldGone = heldSpeaker != null && !heldSpeaker.isAlive();
		if (!Objects.equals(candidateId, lastSpeaker) || heldGone) {
			if (clock - lastSpeakerCut >= config.minShotSeconds || heldGone || heldSpeaker == null) {
				lastSpeaker = candidateId;
				lastSpeakerCut = clock;
				heldSpeaker = candidate;
				speakerChanged = true;
			}
		}
		Entity speaker = heldSpeaker != null && heldSpeaker.isAlive() ? heldSpeaker : candidate;

		Scene scene = collectScene(client, config, speaker, tickProgress);
		clock += dt;

		switch (config.mode) {
			case TRIPOD -> {
				applyPose(client, config, dt, tripodPose(scene));
				return true;
			}
			case SIDE_TRACK -> {
				applyPose(client, config, dt, sideTrackPose(scene, config, dt));
				return true;
			}
			case DIALOGUE -> {
				Pose pose = dialoguePose(client, scene, speaker, speakerChanged, config, dt);
				if (pose != null) {
					applyPose(client, config, dt, pose);
					return true;
				}
				// Nobody to talk to — fall through to the ordinary coverage.
			}
			default -> {
			}
		}

		float speed = Math.max(0.05f, config.shotSpeed);
		shotElapsed += dt * speed;

		// A shot that has spent a second and a half jammed against a wall — or one that has been
		// looking at the back of a pillar while the scene happens behind it — is not going to get
		// better. Pick a different angle instead of sitting there.
		boolean stuck = crampedFor > 1.5f || blindFor > 1.0f;
		if (stuck) {
			if (DEBUG) {
				AbsoluteCinema.LOGGER.info("[camera] recomposing: {}",
						blindFor > 1.0f ? "the scene was out of sight" : "shot was stuck against geometry");
			}
			crampedFor = 0.0f;
			blindFor = 0.0f;
		}

		if (shot == null || shotElapsed >= shot.duration || speakerChanged || stuck) {
			if (DEBUG && speakerChanged) {
				AbsoluteCinema.LOGGER.info("[camera] cutting to {}",
						speaker == null ? "the group (nobody speaking)" : speaker.getName().getString());
			}
			framingSpeaker = speaker != null;
			startNewShot(client, scene, config, speakerChanged);
		}

		Pose target = evaluate(shot, shotElapsed / shot.duration, scene, config);
		applyPose(client, config, dt, target);
		probeVisibility(client, scene);
		return true;
	}

	/**
	 * Is the scene actually on screen? Framing maths only knows where people are, not what is
	 * standing between them and the lens — a pillar, a doorway or a staircase can leave a
	 * perfectly composed shot of nothing at all. A few rays a second are enough to notice, and
	 * noticing is all it takes: the shot is then recomposed from somewhere else.
	 */
	private void probeVisibility(MinecraftClient client, Scene scene) {
		if (clock - lastVisibilityProbe < 0.3f || client.world == null || client.player == null) {
			return;
		}
		lastVisibilityProbe = clock;

		int visible = 0;
		int total = 0;
		// Heads only, and at most a handful of them: this runs while the camera is flying.
		for (int i = 1; i < scene.points.size() && total < 8; i += 2) {
			Vec3d head = scene.points.get(i);
			if (pos.squaredDistanceTo(head) < 0.25) {
				visible++;
				total++;
				continue;
			}
			total++;
			HitResult hit = client.world.raycast(new RaycastContext(pos, head,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
			if (hit.getType() != HitResult.Type.BLOCK) {
				visible++;
			}
		}
		if (total == 0) {
			return;
		}
		if (visible * 2 < total) {
			blindFor += 0.3f;
		} else {
			blindFor = 0.0f;
		}
	}

	/** Wall clamping, blending and the follow damping — shared by every mode. */
	private void applyPose(MinecraftClient client, CinemaConfig config, float dt, Pose target) {
		if (config.avoidWalls && client.world != null && client.player != null) {
			// Cast from the subject itself, not from the rule-of-thirds-shifted look point — in a
			// tight room that shifted point can sit inside the wall and break the whole test.
			Vec3d origin = clampOrigin != null ? clampOrigin : target.look;
			target = new Pose(clampAgainstWalls(client.world, client.player, origin, target.pos), target.look);
		}

		if (!hasPose) {
			pos = target.pos;
			look = target.look;
			hasPose = true;
		} else if (blendDuration > 0.0f && blendElapsed < blendDuration) {
			blendElapsed += dt;
			float t = smoothstep(MathHelper.clamp(blendElapsed / blendDuration, 0.0f, 1.0f));
			pos = blendFromPos.lerp(target.pos, t);
			look = blendFromLook.lerp(target.look, t);
		} else if (pos.squaredDistanceTo(target.pos) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
			// The subject teleported. Following that across the world would send the camera on a
			// one-second flight through everything in between.
			pos = target.pos;
			look = target.look;
			blendDuration = 0.0f;
		} else {
			// A short exponential follow keeps wall clamping and people walking around from
			// snapping the frame.
			float follow = 1.0f - (float) Math.exp(-dt / 0.10f);
			pos = pos.lerp(target.pos, follow);
			look = look.lerp(target.look, follow);
		}
	}

	// ---------------------------------------------------------------------------------------
	// Tripod
	// ---------------------------------------------------------------------------------------

	/**
	 * Locked off. The camera stays where it was planted and only turns to keep the scene in
	 * frame, like a camera on a tripod at the edge of a stage.
	 */
	private Pose tripodPose(Scene scene) {
		if (tripodPos == null) {
			// Planted where the player was standing when the mode came on: put the camera down,
			// walk into shot, play the scene.
			tripodPos = scene.center.add(0.0, scene.eyeHeight, 0.0);
		}
		clampOrigin = null;
		return new Pose(tripodPos, scene.center.add(0.0, scene.eyeHeight - 0.15, 0.0));
	}

	/** Moves the tripod to a new spot — bound to the command and the keybind. */
	public void placeTripod(Vec3d position) {
		tripodPos = position;
		hasPose = false;
		blendDuration = 0.0f;
	}

	// ---------------------------------------------------------------------------------------
	// Side track
	// ---------------------------------------------------------------------------------------

	/**
	 * Travels alongside the scene at a fixed angle, keeping pace with it. Swaps sides every so
	 * often, and immediately if the current side runs out of room.
	 */
	private Pose sideTrackPose(Scene scene, CinemaConfig config, float dt) {
		// Same rule as the composed shots: the angle is the camera's, not the subject's, so
		// nobody steers the shot by turning their head.
		if (!hasTrackedFacing) {
			trackedFacing = scene.faceYaw;
			hasTrackedFacing = true;
		} else {
			trackedFacing = followAngle(trackedFacing, scene.faceYaw, dt);
		}

		if (clock - lastSideSwap > SIDE_SWAP_SECONDS) {
			lastSideSwap = clock;
			double left = clearanceTowards(trackedFacing + SIDE_ANGLE * DEG);
			double right = clearanceTowards(trackedFacing - SIDE_ANGLE * DEG);
			trackSide = left >= right ? 1.0 : -1.0;
		}

		double distance = groupDistance(scene, config);
		double angle = trackedFacing + trackSide * SIDE_ANGLE * DEG;
		Vec3d anchor = scene.center.add(
				-Math.sin(angle) * distance,
				scene.eyeHeight + 0.2,
				Math.cos(angle) * distance);

		if (config.handheldDrift) {
			double t = clock;
			double scale = MathHelper.clamp(distance * 0.02, 0.012, 0.06);
			anchor = anchor.add(
					Math.sin(t * 0.47) * scale,
					Math.sin(t * 0.39 + 1.1) * scale * 0.8,
					Math.cos(t * 0.53) * scale);
		}

		Vec3d target = scene.center.add(0.0, scene.eyeHeight - 0.12, 0.0);
		clampOrigin = target;
		return new Pose(anchor, target);
	}

	// ---------------------------------------------------------------------------------------
	// Dialogue — shot, reverse shot
	// ---------------------------------------------------------------------------------------

	/**
	 * Covers a conversation the way a film does. Two participants, one side of the line, and
	 * three framings: the speaker, the listener's reaction, and a two shot holding both.
	 *
	 * <p>The camera never crosses the line between the two of them. Cross it and the pair swap
	 * sides of the screen between cuts, which makes the conversation unreadable. Cuts here are
	 * instant on purpose — gliding between opposite angles would fly straight through the people.
	 *
	 * <p>Returns null when there is nobody to talk to, so the caller can fall back.
	 */
	@Nullable
	private Pose dialoguePose(MinecraftClient client, Scene scene, @Nullable Entity speaker,
			boolean speakerChanged, CinemaConfig config, float dt) {
		Entity first = speaker != null ? speaker : client.player;
		Entity second = keepPartner(client, first, config);
		if (first == null || second == null) {
			return null;
		}

		// A new voice takes over the frame at once.
		if (speakerChanged || dialoguePhaseLeft <= 0.0f || dialogueFirst != first
				|| dialogueSecond != second || !dialogueFirst.isAlive() || !dialogueSecond.isAlive()) {
			advanceDialoguePhase(first, second, speakerChanged);
		}
		dialoguePhaseLeft -= dt;

		Entity subject = dialoguePhase == DialoguePhase.ON_LISTENER ? dialogueSecond : dialogueFirst;
		Vec3d firstEye = dialogueFirst.getLerpedPos(scene.tickProgress)
				.add(0.0, eyeOffset(dialogueFirst), 0.0);
		Vec3d secondEye = dialogueSecond.getLerpedPos(scene.tickProgress)
				.add(0.0, eyeOffset(dialogueSecond), 0.0);

		Vec3d axis = secondEye.subtract(firstEye);
		axis = new Vec3d(axis.x, 0.0, axis.z);
		if (axis.lengthSquared() < 1.0E-4) {
			return null;
		}
		double separation = axis.length();
		axis = axis.multiply(1.0 / separation);
		Vec3d perpendicular = new Vec3d(-axis.z, 0.0, axis.x).multiply(dialogueSide);

		if (dialoguePhase == DialoguePhase.TWO_SHOT) {
			Vec3d middle = firstEye.add(secondEye).multiply(0.5);
			double back = separation * 0.85 + 2.3;
			Vec3d anchor = middle.add(perpendicular.multiply(back)).add(0.0, 0.45, 0.0);
			clampOrigin = middle;
			return new Pose(withDrift(anchor, back, config), middle);
		}

		// Over the far shoulder, angled off the line so we see the face and not a profile.
		Vec3d subjectEye = subject == dialogueFirst ? firstEye : secondEye;
		Vec3d awayFromSubject = subject == dialogueFirst ? axis : axis.multiply(-1.0);
		double distance = dialogueDistance;
		Vec3d anchor = subjectEye
				.add(awayFromSubject.multiply(distance))
				.add(perpendicular.multiply(distance * 0.42))
				.add(0.0, 0.08, 0.0);

		clampOrigin = subjectEye;
		return new Pose(withDrift(anchor, distance, config), subjectEye);
	}

	/**
	 * The person on the other end of the conversation, held for as long as the pair is plausibly
	 * still talking.
	 *
	 * <p>Re-picking every frame meant the choice moved with the subject's gaze, and since a new
	 * pair means a new cut, someone glancing sideways could re-cut the whole scene. The pair only
	 * changes when it has to: when the partner leaves, dies, or the conversation moves on.
	 */
	@Nullable
	private Entity keepPartner(MinecraftClient client, @Nullable Entity subject, CinemaConfig config) {
		if (subject == null) {
			return null;
		}
		Entity known = null;
		if (subject == dialogueFirst) {
			known = dialogueSecond;
		} else if (subject == dialogueSecond) {
			// The other one started talking: same pair, the roles simply swap.
			known = dialogueFirst;
		}
		if (known != null && known != subject && known.isAlive()
				&& known.squaredDistanceTo(subject) <= config.sceneRadius * config.sceneRadius) {
			return known;
		}
		return findConversationPartner(client, subject, config);
	}

	private void advanceDialoguePhase(Entity speaker, Entity partner, boolean speakerChanged) {
		boolean sameParticipants = dialogueFirst == speaker && dialogueSecond == partner;
		boolean swapped = dialogueFirst == partner && dialogueSecond == speaker && speaker != partner;
		if (swapped) {
			// The line runs the other way now, so the same physical side of the room is the
			// opposite sign. Getting this wrong is exactly what crossing the line looks like.
			dialogueSide = -dialogueSide;
			sameParticipants = true;
		}
		dialogueFirst = speaker;
		dialogueSecond = partner;

		if (!sameParticipants) {
			// Pick a side of the line once, and keep it for as long as this pair is talking.
			dialogueSide = random.nextBoolean() ? 1.0 : -1.0;
			dialogueCuts = 0;
			dialoguePhase = DialoguePhase.TWO_SHOT;
			dialoguePhaseLeft = 4.0f + random.nextFloat() * 2.0f;
		} else if (speakerChanged) {
			dialoguePhase = DialoguePhase.ON_SPEAKER;
			dialoguePhaseLeft = 5.0f + random.nextFloat() * 3.0f;
			dialogueCuts++;
		} else {
			dialogueCuts++;
			// Reaction shots are short; every fourth cut or so, sit back and hold both of them.
			if (dialogueCuts % 4 == 0) {
				dialoguePhase = DialoguePhase.TWO_SHOT;
				dialoguePhaseLeft = 4.0f + random.nextFloat() * 2.0f;
			} else if (dialoguePhase == DialoguePhase.ON_SPEAKER) {
				dialoguePhase = DialoguePhase.ON_LISTENER;
				dialoguePhaseLeft = 2.4f + random.nextFloat() * 1.2f;
			} else {
				dialoguePhase = DialoguePhase.ON_SPEAKER;
				dialoguePhaseLeft = 5.0f + random.nextFloat() * 3.0f;
			}
		}

		dialogueDistance = 1.7 + random.nextDouble() * 0.6;
		// Instant cut: this is the whole point of the technique.
		hasPose = false;
		blendDuration = 0.0f;
		if (DEBUG) {
			AbsoluteCinema.LOGGER.info("[camera] dialogue cut to {}", dialoguePhase);
		}
	}

	/** The person the subject is most plausibly talking to. */
	@Nullable
	private Entity findConversationPartner(MinecraftClient client, @Nullable Entity subject,
			CinemaConfig config) {
		if (client.world == null || subject == null) {
			return null;
		}
		Vec3d eye = subject.getEyePos();
		Vec3d facing = subject.getRotationVec(1.0f);

		List<Entity> candidates = new ArrayList<>(client.world.getPlayers());
		if (config.includeNamedEntities) {
			// Named creatures are how NPCs are marked, and an NPC is exactly who a scene like
			// this is usually played against.
			Box box = subject.getBoundingBox().expand(config.sceneRadius);
			candidates.addAll(client.world.getEntitiesByClass(LivingEntity.class, box,
					entity -> entity != subject && entity.hasCustomName()));
		}

		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity candidate : candidates) {
			if (candidate == subject) {
				continue;
			}
			Vec3d delta = candidate.getEyePos().subtract(eye);
			double distance = delta.length();
			if (distance < 0.6 || distance > config.sceneRadius) {
				continue;
			}
			// Prefer whoever is both close and roughly in front of them.
			double alignment = delta.normalize().dotProduct(facing);
			double score = distance / Math.max(0.35, alignment + 1.0);
			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}

	private Vec3d withDrift(Vec3d anchor, double distance, CinemaConfig config) {
		if (!config.handheldDrift) {
			return anchor;
		}
		double scale = MathHelper.clamp(distance * 0.02, 0.010, 0.05);
		double t = clock;
		return anchor.add(
				(Math.sin(t * 0.53) + Math.sin(t * 1.27) * 0.4) * scale,
				(Math.sin(t * 0.41 + 1.7) + Math.sin(t * 1.09) * 0.35) * scale * 0.8,
				(Math.cos(t * 0.61) + Math.cos(t * 1.13) * 0.4) * scale);
	}

	// ---------------------------------------------------------------------------------------
	// The scene
	// ---------------------------------------------------------------------------------------

	/**
	 * Everyone worth putting in the frame: the player, other players nearby, and any named
	 * creature (which on a roleplay server is how NPCs are marked).
	 */
	private Scene collectScene(MinecraftClient client, CinemaConfig config, @Nullable Entity speaker,
			float tickProgress) {
		ClientWorld world = client.world;
		Entity self = client.player;
		double radius = config.sceneRadius;

		List<Entity> participants = new ArrayList<>();
		participants.add(self);
		for (AbstractClientPlayerEntity other : world.getPlayers()) {
			if (other != self && other.squaredDistanceTo(self) <= radius * radius) {
				participants.add(other);
			}
		}
		if (config.includeNamedEntities) {
			Box box = self.getBoundingBox().expand(radius);
			for (Entity entity : world.getEntitiesByClass(LivingEntity.class, box,
					entity -> entity != self && entity.hasCustomName())) {
				participants.add(entity);
			}
		}

		if (speaker != null && !participants.contains(speaker)) {
			participants.add(speaker);
		}

		double x = 0.0;
		double y = 0.0;
		double z = 0.0;
		double eye = 0.0;
		double sinYaw = 0.0;
		double cosYaw = 0.0;
		for (Entity entity : participants) {
			Vec3d feet = entity.getLerpedPos(tickProgress);
			x += feet.x;
			y += feet.y;
			z += feet.z;
			eye += eyeOffset(entity);
			double yaw = entity.getYaw(tickProgress) * DEG;
			sinYaw += Math.sin(yaw);
			cosYaw += Math.cos(yaw);
		}
		int count = participants.size();
		Vec3d center = new Vec3d(x / count, y / count, z / count);
		double eyeHeight = eye / count;
		// Circular mean, so two people facing 350 and 10 degrees average to 0, not to 180.
		double faceYaw = Math.atan2(sinYaw, cosYaw);

		double spread = 0.0;
		// Two points per person — the feet and the top of the head — are what the framing test
		// has to keep on screen. Sampling the centre only is how people ended up cut in half.
		List<Vec3d> points = new ArrayList<>(participants.size() * 2);
		for (Entity entity : participants) {
			Vec3d feet = entity.getLerpedPos(tickProgress);
			double dx = feet.x - center.x;
			double dz = feet.z - center.z;
			spread = Math.max(spread, Math.sqrt(dx * dx + dz * dz));
			points.add(feet.add(0.0, 0.1, 0.0));
			points.add(feet.add(0.0, eyeOffset(entity) + 0.22, 0.0));
		}

		double room = measureRoom(world, self, center.add(0.0, eyeHeight, 0.0));
		return new Scene(center, eyeHeight, spread, faceYaw, speaker, count, tickProgress, room, points);
	}

	/**
	 * How much clear space there is around the scene, so shots stay inside the room instead of
	 * being dragged into a wall and clamped there. Eight rays, refreshed a few times a second.
	 */
	private double measureRoom(ClientWorld world, Entity viewer, Vec3d eye) {
		if (clock - lastRoomProbe < 0.4 && lastRoomRadius > 0.0) {
			return lastRoomRadius;
		}
		lastRoomProbe = clock;

		double total = 0.0;
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4.0;
			Vec3d end = eye.add(Math.cos(angle) * MAX_ROOM_RADIUS, 0.0, Math.sin(angle) * MAX_ROOM_RADIUS);
			HitResult hit = world.raycast(new RaycastContext(eye, end,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, viewer));
			double clear = hit.getType() == HitResult.Type.BLOCK
					? hit.getPos().distanceTo(eye)
					: MAX_ROOM_RADIUS;
			dirClearances[i] = clear;
			total += clear;
		}
		lastRoomRadius = total / 8.0;
		return lastRoomRadius;
	}

	/**
	 * Clear distance towards a shot azimuth, interpolated between the eight probes.
	 *
	 * <p>The probes are indexed by world angle in the XZ plane, while a shot azimuth builds its
	 * offset as (-sin, cos) — hence the quarter turn between the two.
	 */
	private double clearanceTowards(double worldAngle) {
		double probeAngle = worldAngle + Math.PI / 2.0;
		double slot = probeAngle / (Math.PI / 4.0);
		slot = ((slot % 8.0) + 8.0) % 8.0;
		int low = (int) Math.floor(slot);
		int high = (low + 1) % 8;
		double t = slot - low;
		return MathHelper.lerp(t, dirClearances[low], dirClearances[high]);
	}

	/**
	 * Nudges a shot towards a side of the room that can actually hold it. Without this the
	 * director keeps picking angles that end in a wall, and the camera spends its time being
	 * clamped on top of the people it is filming.
	 */
	private double openAzimuth(Scene scene, double preferred, double wanted) {
		double base = scene.faceYaw;
		double bestAngle = preferred;
		double bestClear = clearanceTowards(base + preferred);
		if (bestClear >= wanted) {
			return preferred;
		}
		// No 180 in the list: swinging straight behind the scene is what produces the
		// over-the-shoulder look we do not want outside the speaker mode.
		for (double offset : new double[] { 30.0, -30.0, 60.0, -60.0, 90.0, -90.0, 120.0, -120.0 }) {
			double candidate = preferred + offset * DEG;
			double clear = clearanceTowards(base + candidate);
			if (clear > bestClear) {
				bestClear = clear;
				bestAngle = candidate;
				if (clear >= wanted) {
					break;
				}
			}
		}
		return bestAngle;
	}

	/**
	 * How far back the camera has to sit to hold the whole group — and no further. The shot
	 * distance from the config is the baseline for a single person; a spread-out group pushes it
	 * out just enough to fit, and the room the scene is standing in caps it.
	 */
	private double groupDistance(Scene scene, CinemaConfig config) {
		double needed = Math.max(config.shotDistance, scene.spread * 1.25 + 2.2);
		return Math.min(needed, Math.max(2.0, scene.roomRadius - 0.6));
	}

	@Nullable
	private Entity findSpeaker(MinecraftClient client, CinemaConfig config, UUID speaking) {
		if (client.world == null || client.player == null) {
			return null;
		}
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.getUuid().equals(speaking)) {
				double maxSq = config.speakerMaxDistance * config.speakerMaxDistance;
				return player.squaredDistanceTo(client.player) <= maxSq ? player : null;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------
	// Composing shots
	// ---------------------------------------------------------------------------------------

	private void startNewShot(MinecraftClient client, Scene scene, CinemaConfig config, boolean urgent) {
		if (hasPose && !config.hardCuts) {
			blendFromPos = pos;
			blendFromLook = look;
			blendElapsed = 0.0f;
			// Cutting to a speaker is a touch quicker than an idle change of frame.
			blendDuration = urgent ? Math.min(config.transitionSeconds, 1.0f) : config.transitionSeconds;
		} else {
			blendDuration = 0.0f;
			if (config.hardCuts) {
				hasPose = false;
			}
		}

		openOnFace = urgent && framingSpeaker;
		shot = compose(client, scene, config);
		shotElapsed = 0.0f;
		blindFor = 0.0f;
		lastType = shot.type;
	}

	private Shot compose(MinecraftClient client, Scene scene, CinemaConfig config) {
		boolean tight = scene.roomRadius < TIGHT_ROOM_RADIUS;
		ShotType type = pickType(config, tight);
		Shot s = new Shot();
		s.type = type;
		s.duration = config.shotDuration * (0.75f + random.nextFloat() * 0.5f);
		s.seed = random.nextFloat() * 100.0f;
		s.intimate = framingSpeaker;
		double side = random.nextBoolean() ? 1.0 : -1.0;

		// Distances are factors of the group distance, except for the intimate shots where a
		// fixed distance in blocks is what makes a close-up a close-up.
		double face = FACE_DISTANCE_MIN + random.nextDouble() * (FACE_DISTANCE_MAX - FACE_DISTANCE_MIN);

		switch (type) {
			case ORBIT -> {
				double start = side * (30.0 + random.nextDouble() * 90.0) * DEG;
				s.startAzimuth = start;
				s.endAzimuth = start + side * (35.0 + random.nextDouble() * 45.0) * DEG;
				if (s.intimate) {
					s.startDistance = s.endDistance = face * 1.25;
					s.startHeight = s.endHeight = 0.02;
				} else {
					s.startDistance = s.endDistance = 0.85 + random.nextDouble() * 0.25;
					s.startHeight = s.endHeight = 0.35 + random.nextDouble() * 0.9;
				}
			}
			case DOLLY_IN -> {
				double a = side * random.nextDouble() * (s.intimate ? 30.0 : 55.0) * DEG;
				s.startAzimuth = a;
				s.endAzimuth = a + side * 8.0 * DEG;
				if (s.intimate) {
					s.startDistance = face * 2.1;
					s.endDistance = face;
					s.startHeight = 0.30;
					s.endHeight = 0.04;
				} else {
					s.startDistance = 1.25;
					s.endDistance = 0.80;
					s.startHeight = 1.10;
					s.endHeight = 0.45;
				}
			}
			case DOLLY_OUT -> {
				double a = side * random.nextDouble() * 55.0 * DEG;
				s.startAzimuth = a;
				s.endAzimuth = a - side * 8.0 * DEG;
				s.startDistance = 0.80;
				s.endDistance = 1.30;
				s.startHeight = 0.45;
				s.endHeight = 1.15;
			}
			case CRANE_DOWN -> {
				double a = side * (20.0 + random.nextDouble() * 60.0) * DEG;
				s.startAzimuth = a;
				s.endAzimuth = a + side * 15.0 * DEG;
				s.startDistance = 1.15;
				s.endDistance = 0.90;
				s.startHeight = 3.20;
				s.endHeight = 0.55;
			}
			case CRANE_UP -> {
				double a = side * (20.0 + random.nextDouble() * 60.0) * DEG;
				s.startAzimuth = a;
				s.endAzimuth = a - side * 15.0 * DEG;
				s.startDistance = 0.85;
				s.endDistance = 1.20;
				s.startHeight = 0.30;
				s.endHeight = 2.60;
			}
			case CLOSE_UP -> {
				double a = side * (8.0 + random.nextDouble() * 26.0) * DEG;
				s.startAzimuth = a;
				s.endAzimuth = a + side * 10.0 * DEG;
				s.startDistance = face;
				s.endDistance = face * 0.92;
				s.startHeight = s.endHeight = 0.06;
				s.duration *= 0.8f;
			}
			case WIDE -> {
				double a = side * (25.0 + random.nextDouble() * 110.0) * DEG;
				s.startAzimuth = a;
				s.endAzimuth = a + side * 12.0 * DEG;
				s.startDistance = 1.40;
				s.endDistance = 1.30;
				s.startHeight = 1.90;
				s.endHeight = 1.50;
			}
			case OVER_SHOULDER -> {
				double a = (150.0 + random.nextDouble() * 60.0) * DEG * side;
				s.startAzimuth = a;
				s.endAzimuth = a + side * 6.0 * DEG;
				s.startDistance = 1.15;
				s.endDistance = 1.05;
				s.startHeight = s.endHeight = 0.55;
			}
			case TRACKING -> {
				double a = side * (75.0 + random.nextDouble() * 30.0) * DEG;
				s.startAzimuth = a - side * 25.0 * DEG;
				s.endAzimuth = a + side * 25.0 * DEG;
				if (s.intimate) {
					s.startDistance = s.endDistance = face * 1.4;
					s.startHeight = s.endHeight = 0.02;
				} else {
					s.startDistance = s.endDistance = 0.85 + random.nextDouble() * 0.25;
					s.startHeight = s.endHeight = 0.25;
				}
			}
			case STATIC_DRIFT -> {
				double a = side * (20.0 + random.nextDouble() * 120.0) * DEG;
				s.startAzimuth = s.endAzimuth = a;
				s.startDistance = s.endDistance = 1.05 + random.nextDouble() * 0.35;
				s.startHeight = s.endHeight = 0.8 + random.nextDouble() * 1.0;
				s.worldLocked = true;
				s.duration *= 1.15f;
			}
		}

		// In a small room, look down on the scene instead of squeezing past it at eye level:
		// there is always headroom, even when there is no floor space.
		if (tight && !s.intimate) {
			s.startHeight += 1.1;
			s.endHeight += 0.8;
			s.startDistance *= 0.75;
			s.endDistance *= 0.75;
		}

		// Aim the shot at a side of the room that can hold it.
		double reach = Math.max(s.startDistance, s.endDistance) * (s.intimate ? 1.0 : groupDistance(scene, config));
		double adjusted = openAzimuth(scene, s.startAzimuth, reach + 0.7);
		double shift = adjusted - s.startAzimuth;
		s.startAzimuth += shift;
		s.endAzimuth += shift;

		// Keep the camera off the backs of people's heads: an orbit is free to swing wide, but
		// not all the way behind the scene, which is what read as an over-the-shoulder shot.
		s.startAzimuth = limitAzimuth(s.startAzimuth, FRONT_ARC);
		s.endAzimuth = limitAzimuth(s.endAzimuth, FRONT_ARC);
		if (Math.abs(s.endAzimuth - s.startAzimuth) < 6.0 * DEG && s.type == ShotType.ORBIT) {
			// The clamp flattened the arc — swing it back towards the front instead.
			s.endAzimuth = s.startAzimuth - Math.signum(s.startAzimuth) * 40.0 * DEG;
		}

		if (config.ruleOfThirds) {
			s.framingOffset = (random.nextBoolean() ? 1.0 : -1.0) * (0.10 + random.nextDouble() * 0.09);
		}
		return s;
	}

	private ShotType pickType(CinemaConfig config, boolean tight) {
		if (openOnFace) {
			openOnFace = false;
			return random.nextBoolean() ? ShotType.CLOSE_UP : ShotType.DOLLY_IN;
		}

		ShotType[] pool;
		if (tight && !framingSpeaker) {
			// A 3x3 room has no floor space for travelling shots — everything that is left is
			// looking down from a corner of the ceiling.
			pool = new ShotType[] { ShotType.CRANE_DOWN, ShotType.STATIC_DRIFT, ShotType.ORBIT };
		} else if (framingSpeaker) {
			// Someone is talking: stay on their face. No over-the-shoulder here — it would frame
			// the back of the speaker's head.
			pool = new ShotType[] { ShotType.CLOSE_UP, ShotType.CLOSE_UP, ShotType.DOLLY_IN,
					ShotType.ORBIT, ShotType.TRACKING };
		} else {
			// Filming the room: wide, moving frames that hold everyone. Deliberately no close-up
			// and no over-the-shoulder, so approaching a face always means something.
			pool = new ShotType[] { ShotType.ORBIT, ShotType.WIDE, ShotType.TRACKING,
					ShotType.CRANE_DOWN, ShotType.CRANE_UP, ShotType.DOLLY_IN, ShotType.DOLLY_OUT,
					ShotType.STATIC_DRIFT };
		}

		for (int attempt = 0; attempt < 6; attempt++) {
			ShotType candidate = pool[random.nextInt(pool.length)];
			if (candidate != lastType) {
				return candidate;
			}
		}
		return pool[random.nextInt(pool.length)];
	}

	// ---------------------------------------------------------------------------------------
	// Playing them back
	// ---------------------------------------------------------------------------------------

	private Pose evaluate(Shot s, float rawProgress, Scene scene, CinemaConfig config) {
		float progress = smoothstep(MathHelper.clamp(rawProgress, 0.0f, 1.0f));
		double azimuth = MathHelper.lerp(progress, s.startAzimuth, s.endAzimuth);
		double heightOffset = MathHelper.lerp(progress, s.startHeight, s.endHeight);

		Vec3d target;
		double desiredFacing;
		double distance;
		boolean onFace = s.intimate && scene.speaker != null && scene.speaker.isAlive();

		if (onFace) {
			// Framed on one face: fixed distances, and the angle is measured off their own gaze.
			target = scene.speaker.getLerpedPos(scene.tickProgress);
			desiredFacing = scene.speaker.getYaw(scene.tickProgress) * DEG;
			distance = MathHelper.lerp(progress, s.startDistance, s.endDistance);
			heightOffset += eyeOffset(scene.speaker) - 0.08;
			clampOrigin = target.add(0.0, eyeOffset(scene.speaker) - 0.08, 0.0);
		} else {
			target = scene.center;
			desiredFacing = scene.faceYaw;
			distance = MathHelper.lerp(progress, s.startDistance, s.endDistance) * groupDistance(scene, config);
			heightOffset += scene.eyeHeight - 0.10;
			clampOrigin = target.add(0.0, scene.eyeHeight - 0.15, 0.0);
		}

		// The shot owns its angle: whoever is on screen may look wherever they like without
		// dragging the camera round with them.
		if (!s.facingSet) {
			s.facing = desiredFacing;
			s.facingSet = true;
		} else {
			s.facing = followAngle(s.facing, desiredFacing, CinemaManager.getFrameDelta());
		}

		double angle = s.facing + azimuth;
		Vec3d offset = new Vec3d(-Math.sin(angle) * distance, heightOffset, Math.cos(angle) * distance);
		Vec3d anchor = s.worldLocked ? lockedAnchor(s, target, offset) : target.add(offset);

		Vec3d lookTarget = s.intimate && scene.speaker != null
				? target.add(0.0, eyeOffset(scene.speaker) - 0.08, 0.0)
				: target.add(0.0, scene.eyeHeight - 0.15, 0.0);

		if (config.handheldDrift) {
			double scale = MathHelper.clamp(distance * 0.02, 0.012, 0.075);
			double t = clock + s.seed;
			anchor = anchor.add(
					(Math.sin(t * 0.53) + Math.sin(t * 1.27) * 0.4) * scale,
					(Math.sin(t * 0.41 + 1.7) + Math.sin(t * 1.09) * 0.35) * scale * 0.8,
					(Math.cos(t * 0.61) + Math.cos(t * 1.13) * 0.4) * scale);
		}

		if (s.framingOffset != 0.0) {
			Vec3d forward = lookTarget.subtract(anchor);
			Vec3d right = new Vec3d(-forward.z, 0.0, forward.x).normalize();
			lookTarget = lookTarget.add(right.multiply(distance * s.framingOffset));
		}

		if (config.keepEveryoneInFrame && !onFace) {
			anchor = fitEveryone(anchor, lookTarget, scene, config);
		}

		return new Pose(anchor, lookTarget);
	}

	/**
	 * Backs the camera off along its own axis until every participant is inside the picture.
	 *
	 * <p>The old framing only knew how far apart people were standing, which says nothing about
	 * how much of the lens they take up: three people spread across the lens need a very
	 * different distance depending on whether the camera is looking along the line they form or
	 * across it. This measures the real thing — each person's offset from the axis of the shot,
	 * against the actual field of view — and moves back by exactly what is missing.
	 *
	 * <p>The letterbox is part of the sum: the mattes eat the top and bottom of the frame, so the
	 * usable vertical angle is smaller than the game's field of view by however tall the bars are.
	 */
	private Vec3d fitEveryone(Vec3d anchor, Vec3d lookTarget, Scene scene, CinemaConfig config) {
		if (scene.points.isEmpty()) {
			return anchor;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d forward = lookTarget.subtract(anchor);
		if (forward.lengthSquared() < 1.0E-6) {
			return anchor;
		}
		forward = forward.normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
		if (right.lengthSquared() < 1.0E-6) {
			right = new Vec3d(1.0, 0.0, 0.0);
		}
		right = right.normalize();
		Vec3d up = right.crossProduct(forward).normalize();

		double fov = client.options.getFov().getValue();
		double tanVertical = Math.tan(Math.toRadians(fov) * 0.5);
		double aspect = client.getWindow().getFramebufferHeight() <= 0
				? 16.0 / 9.0
				: (double) client.getWindow().getFramebufferWidth() / client.getWindow().getFramebufferHeight();
		double barShare = config.letterbox ? MathHelper.clamp(config.letterboxSize * 2.0f, 0.0f, 0.7f) : 0.0;
		double tanUp = tanVertical * (1.0 - barShare) * FRAME_MARGIN;
		double tanSide = tanVertical * aspect * FRAME_MARGIN;
		if (tanUp < 1.0E-3 || tanSide < 1.0E-3) {
			return anchor;
		}

		double needed = 0.0;
		for (Vec3d point : scene.points) {
			Vec3d delta = point.subtract(anchor);
			double depth = delta.dotProduct(forward);
			double horizontal = Math.abs(delta.dotProduct(right)) + BODY_HALF_WIDTH;
			double vertical = Math.abs(delta.dotProduct(up));
			// Pulling back by d adds d to every depth and leaves the offsets alone, so what is
			// missing is simply the depth the widest offset asks for minus the depth we have.
			needed = Math.max(needed, horizontal / tanSide - depth);
			needed = Math.max(needed, vertical / tanUp - depth);
		}
		if (needed <= 0.0) {
			return anchor;
		}
		// Backing off has a limit: somebody who wandered off behind the camera would otherwise
		// send it into orbit. Past the limit the answer is a different angle, not a longer lens,
		// so the shot is marked for recomposition instead.
		if (needed > MAX_FIT_PULLBACK) {
			blindFor += CinemaManager.getFrameDelta();
			needed = MAX_FIT_PULLBACK;
		}
		return anchor.subtract(forward.multiply(needed));
	}

	/**
	 * Turns an angle towards another one, ignoring small changes and never turning faster than
	 * {@link #FACING_FOLLOW_RATE}. This is what keeps other people's mouse movement out of the
	 * camera while still following someone who genuinely turns around.
	 */
	private static double followAngle(double current, double desired, float dt) {
		double difference = MathHelper.wrapDegrees(Math.toDegrees(desired - current)) * DEG;
		double magnitude = Math.abs(difference);
		if (magnitude <= FACING_DEAD_ZONE) {
			return current;
		}
		double step = Math.min(magnitude - FACING_DEAD_ZONE, FACING_FOLLOW_RATE * dt);
		return current + Math.signum(difference) * step;
	}

	private Vec3d lockedAnchor(Shot s, Vec3d target, Vec3d offset) {
		if (s.anchor == null) {
			s.anchor = target.add(offset);
		}
		return s.anchor;
	}

	/**
	 * Keeps the camera inside the room: it may not end up behind a wall, inside one, or so close
	 * to a surface that the near plane cuts through it. Same idea as vanilla's third-person
	 * clipping, but with the corners of the near plane checked too — a single centre ray lets the
	 * lens poke through wall edges.
	 */
	private Vec3d clampAgainstWalls(ClientWorld world, Entity viewer, Vec3d from, Vec3d to) {
		Vec3d direction = to.subtract(from);
		double length = direction.length();
		if (length < 0.05) {
			return to;
		}
		Vec3d unit = direction.multiply(1.0 / length);
		Vec3d right = new Vec3d(-unit.z, 0.0, unit.x).normalize().multiply(0.14);
		Vec3d up = new Vec3d(0.0, 0.14, 0.0);

		double allowed = length;
		for (int corner = 0; corner < 5; corner++) {
			Vec3d nudge = switch (corner) {
				case 1 -> right;
				case 2 -> right.multiply(-1.0);
				case 3 -> up;
				case 4 -> up.multiply(-1.0);
				default -> Vec3d.ZERO;
			};
			HitResult hit = world.raycast(new RaycastContext(from.add(nudge), to.add(nudge),
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, viewer));
			if (hit.getType() == HitResult.Type.BLOCK) {
				allowed = Math.min(allowed, hit.getPos().distanceTo(from.add(nudge)) - 0.30);
			}
		}

		// Whatever little room the wall leaves is all we get — forcing a minimum distance here
		// is exactly what used to shove the camera through the wall of a tight room. If the
		// resulting frame is unusably close, the cramped-shot logic recomposes instead.
		allowed = MathHelper.clamp(allowed, 0.25, length);

		// Inside the subject is worse than any wall: bail out of such a shot within a fraction
		// of a second instead of the usual second and a half.
		severelyCramped = allowed < PERSONAL_SPACE && length > PERSONAL_SPACE;
		if (severelyCramped) {
			crampedFor += CinemaManager.getFrameDelta() * 5.0f;
		} else if (allowed < length * CRAMPED_RATIO) {
			crampedFor += CinemaManager.getFrameDelta();
		} else {
			crampedFor = 0.0f;
		}

		Vec3d clamped = from.add(unit.multiply(allowed));
		if (!severelyCramped) {
			return clamped;
		}

		// Walls left us standing in the subject. There is no floor space, but there is usually
		// headroom: climb until we are out of them, as far as the ceiling allows.
		double needed = Math.sqrt(Math.max(0.0, PERSONAL_SPACE * PERSONAL_SPACE - allowed * allowed));
		Vec3d ceilingProbe = clamped.add(0.0, needed + 0.4, 0.0);
		HitResult above = world.raycast(new RaycastContext(clamped, ceilingProbe,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, viewer));
		double lift = above.getType() == HitResult.Type.BLOCK
				? Math.max(0.0, above.getPos().distanceTo(clamped) - 0.35)
				: needed;
		return clamped.add(0.0, lift, 0.0);
	}

	/** Wraps an azimuth to the shortest equivalent, then holds it inside the given front arc. */
	private static double limitAzimuth(double azimuth, double limitDegrees) {
		double degrees = MathHelper.wrapDegrees(Math.toDegrees(azimuth));
		return MathHelper.clamp(degrees, -limitDegrees, limitDegrees) * DEG;
	}

	private static double eyeOffset(Entity entity) {
		return Math.max(0.4, entity.getEyePos().y - entity.getPos().y);
	}

	private static float smoothstep(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	private record Pose(Vec3d pos, Vec3d look) {
	}

	/** Who is in front of the camera this frame, reduced to what the shots actually need. */
	private record Scene(Vec3d center, double eyeHeight, double spread, double faceYaw,
			@Nullable Entity speaker, int participants, float tickProgress, double roomRadius,
			List<Vec3d> points) {
	}

	private static final class Shot {
		ShotType type = ShotType.ORBIT;
		float duration = 8.0f;
		double startAzimuth;
		double endAzimuth;
		/** Factor of the group distance, or blocks when {@link #intimate}. */
		double startDistance = 1.0;
		double endDistance = 1.0;
		/** Height above the subject's eye level, in blocks. */
		double startHeight;
		double endHeight;
		double framingOffset;
		float seed;
		boolean worldLocked;
		boolean intimate;
		/** World angle the shot is built around, owned by the shot rather than read off a player. */
		double facing;
		boolean facingSet;
		@Nullable
		Vec3d anchor;
	}
}
