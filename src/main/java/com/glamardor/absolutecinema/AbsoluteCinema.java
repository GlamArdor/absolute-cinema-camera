package com.glamardor.absolutecinema;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AbsoluteCinema {
	public static final String MOD_ID = "absolutecinema";
	public static final String MOD_NAME = "Absolute Cinema Camera";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private AbsoluteCinema() {
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
