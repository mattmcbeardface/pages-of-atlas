package com.pagesofatlas;

import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/*
 * Carries PagesOfAtlas physical-page information through
 * Fabric / Indigo's quad pipeline without modifying UVs.
 *
 * Continuity 3.0.1 does not use Fabric's quad tag field.
 *
 * Layout:
 *
 * upper 16 bits = PagesOfAtlas marker
 * lower 8 bits  = physical atlas page
 */
public final class PagesOfAtlasQuadTag {

    private PagesOfAtlasQuadTag() {}

    private static final int MAGIC =
        0x53410000;

    private static final int MAGIC_MASK =
        0xFFFF0000;

    private static final int PAGE_MASK =
        0x000000FF;

    public static int encodePage(
        int page
    ) {
        return MAGIC
            | (page & PAGE_MASK);
    }

    public static int encodeSprite(
        TextureAtlasSprite sprite
    ) {
        if (sprite instanceof PagedSprite paged) {
            return encodePage(
                paged.pagesofatlas$getPage()
            );
        }

        return encodePage(0);
    }

    public static boolean isPagesOfAtlasTag(
        int tag
    ) {
        return (tag & MAGIC_MASK)
            == MAGIC;
    }

    public static int page(
        int tag
    ) {
        if (!isPagesOfAtlasTag(tag)) {
            return 0;
        }

        return tag & PAGE_MASK;
    }
}
