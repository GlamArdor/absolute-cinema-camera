package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.render.PlayerFade;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players answer for themselves whether their armour and held items are drawn, without asking the
 * renderer they inherit from – so the same answer has to be given here, or a faded passer-by would
 * keep a full set of armour hanging in the air where they used to be.
 */
@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {
	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z",
			at = @At("HEAD"), cancellable = true)
	private void absolutecinema$dropFeatures(AvatarRenderState state,
			CallbackInfoReturnable<Boolean> cir) {
		if (!PlayerFade.keepsFeatures(PlayerFade.opacity(state))) {
			cir.setReturnValue(false);
		}
	}
}
