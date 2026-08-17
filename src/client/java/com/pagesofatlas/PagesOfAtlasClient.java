package com.pagesofatlas;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PagesOfAtlasClient implements ClientModInitializer {

    public static final String MOD_ID = "pagesofatlas";

    public static final Logger LOGGER =
        LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("PagesOfAtlas initialized");
    }
}
