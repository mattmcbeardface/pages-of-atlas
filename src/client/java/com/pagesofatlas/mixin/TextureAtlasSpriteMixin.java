package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasClient;
import com.pagesofatlas.PagesOfAtlasRegistry;
import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin
    implements PagedSprite {

    @Unique
    private int pagesofatlas$page = 0;

    @Override
    public int pagesofatlas$getPage() {
        return pagesofatlas$page;
    }

    @Override
    public void pagesofatlas$setPage(
        int page
    ) {
        pagesofatlas$page =
            page;
    }

    /*
     * Tag every block-atlas sprite with its physical
     * PagesOfAtlas page.
     *
     * IMPORTANT:
     *
     * We intentionally DO NOT modify getU(), getU0(),
     * or getU1() here anymore.
     *
     * Continuity must see ordinary atlas UV coordinates
     * while performing CTM interpolation.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void pagesofatlas$tagSprite(
        Identifier atlasLocation,
        SpriteContents contents,
        int atlasWidth,
        int atlasHeight,
        int x,
        int y,
        int padding,
        CallbackInfo ci
    ) {
        PagesOfAtlasRegistry.lookup(
            atlasLocation,
            contents.name()
        ).ifPresent(
            placement -> {

                pagesofatlas$page =
                    placement.page();

                if (placement.page() > 0) {
                    PagesOfAtlasClient.LOGGER.debug(
                        "Tagged sprite {} as atlas page {}",
                        contents.name(),
                        placement.page()
                    );
                }
            }
        );
    }
}
