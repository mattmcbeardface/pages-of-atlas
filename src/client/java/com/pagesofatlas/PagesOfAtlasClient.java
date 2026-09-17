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

        if (PagesOfAtlasVirtualAtlas.enabled()) {
            LOGGER.warn(
                "[VIRTUAL ATLAS POC] Enabled: block terrain uses a logical 32768x32768 atlas backed by four physical pages"
            );
        } else {
            LOGGER.info(
                "[VIRTUAL ATLAS POC] Disabled; using the legacy page-propagation path (enable with -D{}=true or {})",
                PagesOfAtlasVirtualAtlas.PROPERTY,
                PagesOfAtlasVirtualAtlas.ENABLE_MARKER
            );
        }
    }
}
