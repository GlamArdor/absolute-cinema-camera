package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.render.CinemaPostProcessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
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
	private MinecraftClient client;

	/**
	 * Straight after the world (and its entity outlines) are on the main framebuffer, and before
	 * any GUI is drawn — so the bars and HUD never get blurred or graded.
	 */
	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V",
			shift = At.Shift.AFTER))
	private void absolutecinema$applyFilters(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
		CinemaPostProcessor.render(this.client, this.client.getFramebuffer());
	}

	/** No first-person arm in a cutscene — and in the directed modes it would hang in mid air. */
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$hideHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
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
	 * smoothing the camera position never removed it — the picture kept rocking from side to side
	 * on every step, and no camera on a real set does that.
	 */
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$noBob(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
		if (absolutecinema$stabilized()) {
			ci.cancel();
		}
	}

	/** Same for the jolt when something hits you: a camera does not flinch. */
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$noHurtTilt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
		if (absolutecinema$stabilized()) {
			ci.cancel();
		}
	}

	@Unique
	private boolean absolutecinema$stabilized() {
		return CinemaManager.isVisible() && CinemaConfig.get().stabilizeCamera;
	}
}
