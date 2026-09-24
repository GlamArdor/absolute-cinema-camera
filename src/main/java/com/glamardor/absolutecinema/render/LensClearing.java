package com.glamardor.absolutecinema.render;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CinemaConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Clears the lens.
 *
 * <p>The camera is clipped against solid geometry, but a sign, a banner or an item frame has no
 * collision at all, so nothing stops the camera ending up with its nose against one – and a
 * signboard half a block from the lens is the entire picture. Since the camera cannot be pushed
 * out of something it does not collide with, the clutter is hidden instead: anything in front of
 * the lens and closer than the configured distance is simply not drawn for that frame.
 *
 * <p>Only the directed modes, where the mod is flying the camera and the player cannot step
 * aside – in first person, things disappearing in front of you would just be confusing.
 */
public final class LensClearing {
	/**
	 * How far off the lens axis something may sit and still count as being in the way.
	 *
	 * <p>Two thresholds, because the angle a thing covers depends on how close it is. At the far
	 * edge of the clearing distance only what the camera is more or less pointed at matters; right
	 * up against the lens, a sign a long way off axis still fills a corner of the frame. A single
	 * threshold is what let signs survive at the edge of shot.
	 */
	private static final double IN_FRONT_FAR = 0.15;
	private static final double IN_FRONT_NEAR = -0.35;

	private LensClearing() {
	}

	public static boolean hides(Vec3d point) {
		if (!CinemaManager.isVisible()) {
			return false;
		}
		CinemaConfig config = CinemaConfig.get();
		if (!config.hideNearbyBlockers || !config.mode.isDirected()) {
			return false;
		}
		return inTheWay(point, config.blockerDistance) > 0.0f;
	}

	/**
	 * How far into the lens something sitting at this point has come: 0 when it is not in the way
	 * at all, 1 when it is on the glass.
	 *
	 * <p>The clutter hiding only needs to know whether this is above zero. The people fading needs
	 * the number itself, because a person is not switched off the moment they qualify – they are
	 * faded by however much of the way in they are.
	 */
	public static float inTheWay(Vec3d point, double radius) {
		Camera camera = camera();
		if (camera == null || radius <= 0.0) {
			return 0.0f;
		}

		Vec3d delta = point.subtract(camera.getPos());
		double distanceSquared = delta.lengthSquared();
		if (distanceSquared > radius * radius) {
			return 0.0f;
		}
		if (distanceSquared < 1.0E-6) {
			return 1.0f;
		}
		Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		// Widens as it gets closer: at the edge of the radius the lens has to be pointed at it, on
		// the lens itself anything short of directly behind counts.
		double nearness = 1.0 - Math.sqrt(distanceSquared) / radius;
		double threshold = IN_FRONT_FAR + (IN_FRONT_NEAR - IN_FRONT_FAR) * nearness;
		if (delta.normalize().dotProduct(forward) <= threshold) {
			return 0.0f;
		}
		return (float) nearness;
	}

	@Nullable
	public static Camera camera() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.gameRenderer == null) {
			return null;
		}
		Camera camera = client.gameRenderer.getCamera();
		return camera != null && camera.isReady() ? camera : null;
	}
}
