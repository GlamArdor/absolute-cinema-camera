package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.render.CinemaOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class InGameHudMixin {
	@Shadow
	protected abstract void extractChat(GuiGraphicsExtractor context, DeltaTracker tickCounter);

	/**
	 * The bars are drawn first, so that a HUD the player chose to keep stays readable on top of
	 * them – a 12% bar covers the whole hotbar otherwise. When the HUD is being hidden the rest
	 * of the method is skipped, but the chat is drawn back in by hand: on a roleplay server half
	 * the scene happens in text, and losing it would make the mode unusable.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$drawBars(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		CinemaOverlay.render(context);
		if (!CinemaOverlay.shouldHideHud()) {
			return;
		}
		if (CinemaOverlay.shouldKeepChat()) {
			// Lifted clear of the bottom matte, so the text never sits half inside it.
			int lift = CinemaOverlay.barHeight(context);
			context.pose().pushMatrix();
			context.pose().translate(0.0f, -lift);
			extractChat(context, tickCounter);
			context.pose().popMatrix();
		}
		ci.cancel();
	}

	/**
	 * No crosshair in a film. It survives when the HUD is deliberately kept – for the chat, or the
	 * hotbar – and there it is worse than pointless: in the directed modes the camera is nowhere
	 * near the player's eyes, so the mark sits in the middle of the picture pointing at nothing.
	 *
	 * <p>Cancelled at the drawing, not at {@code shouldRenderCrosshair}. That method reads as the
	 * obvious place and is not: it asks whether the <em>debug</em> crosshair – the little F3 axis
	 * cross – should replace the ordinary one, and the ordinary one is drawn when it returns false.
	 * Forcing it false therefore guarantees the crosshair rather than removing it.
	 */
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void absolutecinema$hideCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter,
			CallbackInfo ci) {
		if (CinemaManager.isVisible()) {
			ci.cancel();
		}
	}
}
