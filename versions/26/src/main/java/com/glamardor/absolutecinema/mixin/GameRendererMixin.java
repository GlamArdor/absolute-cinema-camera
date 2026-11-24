package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.render.CinemaPostProcessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Straight after the world is on the main framebuffer, and before any GUI is drawn – so the
	 * bars and HUD never get blurred or graded.
	 *
	 * <p>Earlier than in 1.21.8, which ran after the entity outlines: from 26.x the main depth
	 * buffer is cleared for the first-person hand as soon as the level is done (unless a post
	 * effect asked for consistent depth), and the depth of field needs the level's depth.
	 */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
			shift = At.Shift.AFTER))
	private void absolutecinema$applyFilters(CallbackInfo ci) {
		CinemaPostProcessor.render(this.minecraft, ((GameRenderer) (Object) this).mainRenderTarget());
	}

	/** No first-person arm in a cutscene – and in the directed modes it would hang in mid air. */
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$hideHand(CameraRenderState cameraState, PlayerRenderState playerState,
			GpuTextureView depthTextureView, CallbackInfo ci) {
		if (!CinemaManager.isVisible()) {
			return;
		}
		CinemaConfig config = CinemaConfig.get();
		if (config.hideHand || config.mode.isDirected()) {
			ci.cancel();
		}
	}

	/**
	 * The walking sway. It is applied to the view matrix rather than to the camera, which is why
	 * smoothing the camera position never removed it – the picture kept rocking from side to side
	 * on every step, and no camera on a real set does that.
	 */
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$noBob(CameraRenderState cameraState, PoseStack matrices, CallbackInfo ci) {
		if (absolutecinema$stabilized()) {
			ci.cancel();
		}
	}

	/** Same for the jolt when something hits you: a camera does not flinch. */
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$noHurtTilt(CameraRenderState cameraState, PoseStack matrices, CallbackInfo ci) {
		if (absolutecinema$stabilized()) {
			ci.cancel();
		}
	}

	@Unique
	private boolean absolutecinema$stabilized() {
		return CinemaManager.isVisible() && CinemaConfig.get().stabilizeCamera;
	}
}
