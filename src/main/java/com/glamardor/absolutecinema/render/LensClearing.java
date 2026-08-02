package com.glamardor.absolutecinema.render;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CinemaConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Clears the lens.
 *
 * <p>The camera is clipped against solid geometry, but a sign, a banner or an item frame has no
 * collision at all, so nothing stops the camera ending up with its nose against one — and a
 * signboard half a block from the lens is the entire picture. Since the camera cannot be pushed
 * out of something it does not collide with, the clutter is hidden instead: anything in front of
 * the lens and closer than the configured distance is simply not drawn for that frame.
 *
 * <p>Only the directed modes, where the mod is flying the camera and the player cannot step
 * aside — in first person, things disappearing in front of you would just be confusing.
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.gameRenderer == null) {
			return false;
		}
		Camera camera = client.gameRenderer.getCamera();
		if (camera == null || !camera.isReady()) {
			return false;
		}

		Vec3d delta = point.subtract(camera.getPos());
		double distanceSquared = delta.lengthSquared();
		double radius = config.blockerDistance;
		if (distanceSquared > radius * radius) {
			return false;
		}
		if (distanceSquared < 1.0E-6) {
			return true;
		}
		Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		// Widens as it gets closer: at the edge of the radius the lens has to be pointed at it, on
		// the lens itself anything short of directly behind counts.
		double nearness = 1.0 - Math.sqrt(distanceSquared) / radius;
		double threshold = IN_FRONT_FAR + (IN_FRONT_NEAR - IN_FRONT_FAR) * nearness;
		return delta.normalize().dotProduct(forward) > threshold;
	}
}
