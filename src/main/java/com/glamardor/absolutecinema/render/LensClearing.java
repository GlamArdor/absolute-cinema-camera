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
	/** Ignore whatever is off to the side or behind: only what the lens is pointed at matters. */
	private static final double IN_FRONT = 0.15;

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
		return delta.normalize().dotProduct(forward) > IN_FRONT;
	}
}
