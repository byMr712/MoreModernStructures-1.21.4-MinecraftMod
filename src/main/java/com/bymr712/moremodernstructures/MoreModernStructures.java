package com.bymr712.moremodernstructures;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoreModernStructures implements ModInitializer {
    public static final String MOD_ID = "moremodernstructures";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("MoreModernStructures loaded. Modern structures registered via datapack.");
    }
}