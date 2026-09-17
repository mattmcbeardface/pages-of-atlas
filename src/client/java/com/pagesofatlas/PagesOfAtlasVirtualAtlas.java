package com.pagesofatlas;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * Constants and the opt-in switch for the 32K virtual-atlas proof.
 *
 * <p>The legacy Pages of Atlas path remains the default. Enable the proof with
 * {@code -Dpagesofatlas.virtualAtlasPoc=true}.</p>
 */
public final class PagesOfAtlasVirtualAtlas {

    private PagesOfAtlasVirtualAtlas() {}

    public static final String PROPERTY =
        "pagesofatlas.virtualAtlasPoc";

    public static final Path ENABLE_MARKER =
        Path.of(
            "mods",
            "pagesofatlas-virtual-atlas-poc.enable"
        );

    public static final int CELL_SIZE = 16_384;
    public static final int VIRTUAL_SIZE = 32_768;
    public static final int PAGE_COUNT = 4;

    private static final boolean ENABLED =
        Boolean.getBoolean(PROPERTY)
            || Files.isRegularFile(ENABLE_MARKER);

    public static boolean enabled() {
        return ENABLED;
    }

    public static boolean enabledFor(
        Identifier atlas
    ) {
        return ENABLED
            && TextureAtlas.LOCATION_BLOCKS.equals(atlas);
    }

    public static int cellX(int page) {
        return page & 1;
    }

    public static int cellY(int page) {
        return (page >> 1) & 1;
    }

    public static int virtualX(
        int page,
        int physicalX
    ) {
        return cellX(page) * CELL_SIZE
            + physicalX;
    }

    public static int virtualY(
        int page,
        int physicalY
    ) {
        return cellY(page) * CELL_SIZE
            + physicalY;
    }
}
