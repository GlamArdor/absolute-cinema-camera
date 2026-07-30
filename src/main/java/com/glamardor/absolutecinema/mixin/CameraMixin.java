package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.camera.CinemaCameraController;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
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
	protected abstract void setPos(Vec3d pos);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	private boolean thirdPerson;

	@Override
	public void applyPos(Vec3d pos) {
		setPos(pos);
	}

	@Override
	public void applyRotation(float yaw, float pitch) {
		setRotation(yaw, pitch);
	}

	@Override
	public void applyThirdPerson(boolean value) {
		this.thirdPerson = value;
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void absolutecinema$direct(BlockView area, Entity focusedEntity, boolean thirdPerson,
			boolean inverseView, float tickProgress, CallbackInfo ci) {
		CinemaCameraController.apply((Camera) (Object) this, this, focusedEntity, tickProgress);
	}
}
