package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.render.CinemaPostProcessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
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
}
