package com.aleksandreev.castlewars;

import com.aleksandreev.castlewars.command.WarsEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CastleWarsMod.MOD_ID)
public final class CastleWarsMod {
    public static final String MOD_ID = "castlewars";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CastleWarsMod() {
        MinecraftForge.EVENT_BUS.register(WarsEvents.class);
        LOGGER.info("Castle Wars initialized");
    }
}
