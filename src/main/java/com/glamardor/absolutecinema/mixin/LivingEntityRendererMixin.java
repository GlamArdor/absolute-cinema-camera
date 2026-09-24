package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.render.PlayerFade;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fades out whoever is standing in the lens.
 *
 * <p>Everything the fade needs to know is about the entity, and by the time a frame is being drawn
 * the renderer has only a render state to go on — so the reading is taken where the state is
 * filled in and carried along on it. From there the body is moved onto a translucent layer and
 * given the alpha to match.
 *
 * <p>The people this applies to are chosen in {@link PlayerFade}: passers-by only, never anybody
 * the camera is filming.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	/** The reading for the body being drawn right now, between the head of render and its end. */
	@Unique
	private float absolutecinema$opacity = 1.0f;

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void absolutecinema$measureFade(LivingEntity entity, LivingEntityRenderState state,
			float tickProgress, CallbackInfo ci) {
		((FabricRenderState) (Object) state).setData(PlayerFade.OPACITY, PlayerFade.opacity(entity));
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void absolutecinema$beginBody(LivingEntityRenderState state, MatrixStack matrices,
			VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		absolutecinema$opacity = PlayerFade.opacity(state);
	}

	/**
	 * Onto the translucent layer. The solid layer ignores alpha entirely, so without this the body
	 * would be drawn at full strength however faint the colour asked for.
	 */
	@ModifyArg(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;"),
			index = 2)
	private boolean absolutecinema$translucentBody(boolean translucent) {
		return translucent || PlayerFade.fading(absolutecinema$opacity);
	}

	@ModifyArg(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"),
			index = 4)
	private int absolutecinema$fadeBody(int color) {
		if (!PlayerFade.fading(absolutecinema$opacity)) {
			return color;
		}
		return ColorHelper.withAlpha(absolutecinema$opacity, color);
	}

	/**
	 * Armour, held items and the cape come off with the fade. They are drawn by feature renderers
	 * on layers of their own, most of them cutout layers that do not blend at all — faded, they
	 * would hang in the air as a solid suit around a ghost.
	 */
	@Inject(method = "shouldRenderFeatures(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)Z",
			at = @At("HEAD"), cancellable = true)
	private void absolutecinema$dropFeatures(LivingEntityRenderState state,
			CallbackInfoReturnable<Boolean> cir) {
		if (!PlayerFade.keepsFeatures(PlayerFade.opacity(state))) {
			cir.setReturnValue(false);
		}
	}

	/** A name tag floating in the middle of the shot is the clutter, with or without its owner. */
	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"),
			cancellable = true)
	private void absolutecinema$dropLabel(LivingEntity entity, double squaredDistance,
			CallbackInfoReturnable<Boolean> cir) {
		if (!PlayerFade.keepsFeatures(PlayerFade.opacity(entity))) {
			cir.setReturnValue(false);
		}
	}
}
