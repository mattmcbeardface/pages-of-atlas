package com.pagesofatlas;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Non-owning logical view of a sprite in the fixed 32K virtual atlas.
 *
 * <p>The matching physical {@link PagedTextureAtlasSprite} is the sole owner
 * of the shared {@link SpriteContents} and is the only view ever uploaded.</p>
 */
public final class LogicalVirtualAtlasSprite
    extends TextureAtlasSprite {

    public LogicalVirtualAtlasSprite(
        Identifier logicalAtlas,
        SpriteContents contents,
        int virtualX,
        int virtualY,
        int padding
    ) {
        super(
            logicalAtlas,
            contents,
            PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE,
            PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE,
            virtualX,
            virtualY,
            padding
        );
    }

    @Override
    public void close() {
        // The physical upload sprite owns the shared SpriteContents.
    }
}
