package com.pagesofatlas;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;

/**
 * A paged sprite that shares SpriteContents owned by another physical atlas.
 *
 * Painting page zero owns the real minecraft:back SpriteContents. Secondary
 * painting pages use this wrapper to upload the same pixels and retain their
 * own page-local UVs without closing the shared NativeImages a second time.
 */
public final class NonOwningPagedTextureAtlasSprite
    extends PagedTextureAtlasSprite {

    public NonOwningPagedTextureAtlasSprite(
        Identifier physicalAtlas,
        SpriteContents contents,
        int atlasWidth,
        int atlasHeight,
        int x,
        int y,
        int padding,
        int page
    ) {
        super(
            physicalAtlas,
            contents,
            atlasWidth,
            atlasHeight,
            x,
            y,
            padding,
            page
        );
    }

    @Override
    public void close() {
        // Page zero owns and closes the shared SpriteContents.
    }
}
