package com.pagesofatlas;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * Production state and constants for the 32K virtual block atlas.
 *
 * <p>The block atlas uses this architecture only when ordinary packing shows
 * that it needs more than one physical texture.</p>
 */
public final class PagesOfAtlasVirtualAtlas {

    private PagesOfAtlasVirtualAtlas() {}

    public static final int CELL_SIZE = 16_384;
    public static final int VIRTUAL_SIZE = 32_768;
    public static final int PAGE_COUNT = 4;

    public static boolean selectForCurrentStitch(
        Identifier atlas,
        boolean pagingRequired
    ) {
        boolean virtual =
            TextureAtlas.LOCATION_BLOCKS.equals(atlas)
                && pagingRequired;

        if (TextureAtlas.LOCATION_BLOCKS.equals(atlas)) {
            PagesOfAtlasRegistry.selectCurrentVirtualMode(
                atlas,
                virtual
            );
        }

        return virtual;
    }

    public static boolean active() {
        return activeFor(
            TextureAtlas.LOCATION_BLOCKS
        );
    }

    public static boolean activeFor(
        Identifier atlas
    ) {
        return TextureAtlas.LOCATION_BLOCKS.equals(atlas)
            && PagesOfAtlasRegistry.virtualMode(atlas);
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
