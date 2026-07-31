package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.render.LensClearing;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Signs, banners, skulls and chests standing right in front of the lens. They have no collision
 * to clip the camera against, so they are dropped from the frame instead.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	@Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;"
			+ "Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$clearLens(BlockEntity blockEntity, float tickProgress, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (LensClearing.hides(Vec3d.ofCenter(blockEntity.getPos()))) {
			ci.cancel();
		}
	}
}
