package com.pagesofatlas;

import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/*
 * PagesOfAtlas-created TextureAtlasSprite.
 *
 * IMPORTANT:
 *
 * UV page encoding is intentionally NOT implemented here.
 *
 * Continuity and other model/rendering mods may create,
 * replace, wrap, or otherwise operate on ordinary
 * TextureAtlasSprite instances.
 *
 * Therefore page encoding is implemented universally by
 * TextureAtlasSpriteMixin.
 */
public class PagedTextureAtlasSprite
    extends TextureAtlasSprite {

    public static final float PAGE_U_STRIDE = 2.0F;

    private final int pagesofatlas$page;

    public PagedTextureAtlasSprite(
        Identifier logicalAtlas,
        SpriteContents contents,
        int atlasWidth,
        int atlasHeight,
        int x,
        int y,
        int padding,
        int page
    ) {
        super(
            logicalAtlas,
            contents,
            atlasWidth,
            atlasHeight,
            x,
            y,
            padding
        );

        this.pagesofatlas$page =
            page;

        /*
         * Explicitly synchronize the universal sprite
         * page tag.
         */
        ((PagedSprite)this)
            .pagesofatlas$setPage(
                page
            );

        if (page > 0) {
            PagesOfAtlasClient.LOGGER.debug(
                "Created paged sprite {} on page {}",
                contents.name(),
                page
            );
        }
    }

    public int pagesofatlas$page() {
        return this.pagesofatlas$page;
    }
}
