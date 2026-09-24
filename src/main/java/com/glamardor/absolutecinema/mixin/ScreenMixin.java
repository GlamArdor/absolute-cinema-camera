package com.glamardor.absolutecinema.mixin;

import com.glamardor.absolutecinema.render.SceneDome;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the world visible behind our own settings screen.
 *
 * <p>The scene radius is drawn around the player while the settings are open, so that the slider
 * can be set by looking at the room rather than by guessing at a number in blocks. A screen
 * normally blurs and darkens whatever is behind it – sensible for a menu, useless when the point of
 * the menu is what is behind it.
 *
 * <p>Only while that drawing is actually up, which is only ever our own settings: every other
 * screen in the game keeps its background exactly as it was.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	/*
	 * Both injections are require = 0 on purpose.
	 *
	 * This is decoration: a clear background behind one settings screen. If a future version of the
	 * game renames or removes either method, the mixin quietly does nothing and the background goes
	 * back to being blurred – which is a cosmetic regression. The alternative, and the default, is
	 * that the mixin fails to apply and the game refuses to start. Nothing that only affects how a
	 * menu looks should ever be able to do that.
	 */
	@Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, require = 0)
	private void absolutecinema$noBlur(DrawContext context, CallbackInfo ci) {
		if (SceneDome.isShowing()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void absolutecinema$noDarkening(DrawContext context, CallbackInfo ci) {
		if (SceneDome.isShowing()) {
			ci.cancel();
		}
	}
}
