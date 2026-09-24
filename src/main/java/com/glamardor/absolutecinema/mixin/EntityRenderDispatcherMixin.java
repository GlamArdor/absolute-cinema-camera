package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.render.LensClearing;
import com.glamardor.absolutecinema.render.PlayerFade;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same for paintings, item frames and text displays – the other half of what tends to be
 * hanging on a roleplay server's walls.
 *
 * <p>Participants are never hidden, whatever they are standing in front of: cutting somebody out
 * of the scene to improve the view of it would be missing the point. Passers-by are another
 * matter, and they get the fade rather than this – see {@link PlayerFade}.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$clearLens(Entity entity, Frustum frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof LivingEntity) {
			// People are faded rather than cut, and only once the fade has run out is the body
			// dropped altogether – at that point it is a smear over the lens and nothing else.
			if (PlayerFade.hidden(entity)) {
				cir.setReturnValue(false);
			}
			return;
		}
		if (LensClearing.hides(entity.getBoundingBox().getCenter())) {
			cir.setReturnValue(false);
		}
	}
}
