package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.camera.CinemaCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla finishes placing the camera, then we move it. Running at TAIL means every vanilla
 * behaviour (sleeping, third person clipping, minecart lerp) has already been applied.
 */
@Mixin(Camera.class)
public abstract class CameraMixin implements CinemaCameraController.CameraAccess {
	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	private boolean detached;

	@Override
	public void applyPos(Vec3 pos) {
		setPosition(pos);
	}

	@Override
	public void applyRotation(float yaw, float pitch) {
		setRotation(yaw, pitch);
	}

	@Override
	public void applyThirdPerson(boolean value) {
		this.detached = value;
	}

	@Shadow
	private Entity entity;

	/**
	 * alignWithEntity is where the camera is put on the player each frame, and it runs before the
	 * culling frustum is built from the camera – so the chunks culled are the ones our shot sees.
	 */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void absolutecinema$direct(float tickProgress, CallbackInfo ci) {
		CinemaCameraController.apply((Camera) (Object) this, this, this.entity, tickProgress);
	}
}
