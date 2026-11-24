package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.render.LensClearing;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Signs, banners, skulls and chests standing right in front of the lens. They have no collision
 * to clip the camera against, so they are dropped from the frame instead.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	/** Never extracted, so never submitted: from 26.x that is the one place to stop a draw. */
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$clearLens(BlockEntity blockEntity, float tickProgress,
			ModelFeatureRenderer.CrumblingOverlay breakProgress, boolean isGloballyRendered,
			CallbackInfoReturnable<BlockEntityRenderState> cir) {
		if (LensClearing.hides(Vec3.atCenterOf(blockEntity.getBlockPos()))) {
			cir.setReturnValue(null);
		}
	}
}
