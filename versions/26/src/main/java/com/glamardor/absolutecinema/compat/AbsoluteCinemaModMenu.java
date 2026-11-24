package com.glamardor.absolutecinema.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Puts the settings button on our entry in the Mod Menu list. */
public class AbsoluteCinemaModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> com.glamardor.absolutecinema.gui.ConfigScreenFactory.create(parent);
	}
}
