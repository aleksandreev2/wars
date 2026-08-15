package com.aleksandreev.castlewars;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CastleWarsMod.MOD_ID)
public final class CastleWarsMod {
    public static final String MOD_ID = "castlewars";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CastleWarsMod() {
        LOGGER.info("Castle Wars initialized");
    }
}
