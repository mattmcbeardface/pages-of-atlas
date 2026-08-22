package com.pagesofatlas;

/**
 * Tracks whether the current Iris rendering pipeline actually
 * requires normal/specular PBR samplers.
 */
public final class PagesOfAtlasPbrDemand {

    private static volatile boolean required;
    private static volatile boolean clearRequested;

    private PagesOfAtlasPbrDemand() {}

    public static synchronized void setRequired(
        boolean value
    ) {
        if (required == value) {
            return;
        }

        boolean wasRequired =
            required;

        required =
            value;

        if (wasRequired && !value) {
            clearRequested =
                true;
        }

        PagesOfAtlasClient.LOGGER.info(
            "[PBR DEMAND] Iris pipeline PBR required={}",
            value
        );
    }

    public static boolean required() {
        return required;
    }

    public static synchronized boolean
        consumeClearRequested() {

        if (!clearRequested) {
            return false;
        }

        clearRequested =
            false;

        return true;
    }
}
