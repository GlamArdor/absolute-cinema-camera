package com.glamardor.absolutecinema.camera;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CinemaConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Bridges the mixin on {@link Camera} and the actual camera work.
 *
 * <p>Everything is weighted by the mod's transition value, so switching the cinema mode on
 * glides the camera out into its first shot instead of teleporting, and switching off glides it
 * back onto the player's own eyes.
 */
public final class CinemaCameraController {
	/** Smoothed first-person state; kept between frames. */
	private static Vec3 smoothPos = Vec3.ZERO;
	private static float smoothYaw;
	private static float smoothPitch;
	private static boolean hasSmoothState;

	/** Distance in blocks past which we assume a teleport and stop smoothing. */
	private static final double TELEPORT_DISTANCE = 6.0;

	private CinemaCameraController() {
	}

	public interface CameraAccess {
		void applyPos(Vec3 pos);

		void applyRotation(float yaw, float pitch);

		void applyThirdPerson(boolean thirdPerson);
	}

	public static void apply(Camera camera, CameraAccess access, Entity focused, float tickProgress) {
		CinemaManager.beginFrame();

		float weight = CinemaManager.getTransition();
		if (weight <= 0.001f) {
			hasSmoothState = false;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		CinemaConfig config = CinemaConfig.get();
		float dt = client.isPaused() ? 0.0f : CinemaManager.getFrameDelta();

		if (config.mode.isDirected()) {
			applyDirected(client, camera, access, config, dt, tickProgress, weight);
		} else {
			applyFirstPerson(camera, access, config, dt, weight);
		}
	}

	private static void applyDirected(Minecraft client, Camera camera, CameraAccess access,
			CinemaConfig config, float dt, float tickProgress, float weight) {
		CameraDirector director = CameraDirector.get();
		if (!director.update(client, dt, tickProgress)) {
			return;
		}

		Vec3 vanillaPos = camera.position();
		float vanillaYaw = camera.yRot();
		float vanillaPitch = camera.xRot();

		float blend = smoothstep(weight);
		Vec3 pos = vanillaPos.lerp(director.getPos(), blend);
		float yaw = Mth.rotLerp(blend, vanillaYaw, director.getYaw());
		float pitch = Mth.lerp(blend, vanillaPitch, director.getPitch());

		access.applyThirdPerson(blend > 0.06f);
		access.applyRotation(yaw, pitch);
		access.applyPos(pos);
		hasSmoothState = false;
	}

	private static void applyFirstPerson(Camera camera, CameraAccess access, CinemaConfig config,
			float dt, float weight) {
		if (!config.smoothCamera) {
			hasSmoothState = false;
			return;
		}

		Vec3 targetPos = camera.position();
		float targetYaw = camera.yRot();
		float targetPitch = camera.xRot();

		if (!hasSmoothState || targetPos.distanceToSqr(smoothPos) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
			smoothPos = targetPos;
			smoothYaw = targetYaw;
			smoothPitch = targetPitch;
			hasSmoothState = true;
			return;
		}

		// tau is the time constant of the follow: bigger smoothing setting, lazier camera.
		float rotationTau = 0.02f + config.rotationSmoothing * 0.30f;
		float positionTau = 0.02f + config.positionSmoothing * 0.22f;
		float rotationStep = 1.0f - (float) Math.exp(-dt / rotationTau);
		float positionStep = 1.0f - (float) Math.exp(-dt / positionTau);

		smoothYaw = Mth.rotLerp(rotationStep, smoothYaw, targetYaw);
		smoothPitch = Mth.lerp(positionStepGuard(rotationStep), smoothPitch, targetPitch);
		smoothPos = smoothPos.lerp(targetPos, positionStep);

		float blend = smoothstep(weight);
		Vec3 pos = targetPos.lerp(smoothPos, blend);
		float yaw = Mth.rotLerp(blend, targetYaw, smoothYaw);
		float pitch = Mth.lerp(blend, targetPitch, smoothPitch);

		access.applyRotation(yaw, pitch);
		access.applyPos(pos);
	}

	private static float positionStepGuard(float step) {
		return Mth.clamp(step, 0.0f, 1.0f);
	}

	private static float smoothstep(float t) {
		float x = Mth.clamp(t, 0.0f, 1.0f);
		return x * x * (3.0f - 2.0f * x);
	}
}
