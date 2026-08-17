package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasQuadTag;
import com.pagesofatlas.PagesOfAtlasRegistry;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/*
 * Continuity CTM compatibility.
 *
 * Continuity normally finds the source sprite for a quad using
 * RenderUtil.getSpriteFinder(), which represents the logical
 * block atlas.
 *
 * PagesOfAtlas quads may physically live on another atlas page.
 * The quad tag tells us which page contains the sprite.
 *
 * Redirect Continuity's SpriteFinder.find(quad) to the finder
 * belonging to that physical page.
 */
@Mixin(
    targets =
        "me.pepperbell.continuity.client.model.CtmBlockStateModel$CtmQuadTransform",
    remap = false
)
public abstract class ContinuityCtmSpriteFinderMixin {

    @Redirect(
        method = "transformOnce",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/fabricmc/fabric/api/client/renderer/v1/sprite/SpriteFinder;find(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadView;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
        ),
        remap = false
    )
    private TextureAtlasSprite pagesofatlas$findSpriteOnCorrectPage(
        SpriteFinder originalFinder,
        QuadView quad
    ) {
        int tag = quad.tag();

        if (!PagesOfAtlasQuadTag.isPagesOfAtlasTag(tag)) {
            return originalFinder.find(quad);
        }

        int page =
            PagesOfAtlasQuadTag.page(tag);

        /*
         * Page zero is the normal Minecraft block atlas,
         * therefore Continuity's original finder is correct.
         */
        if (page <= 0) {
            return originalFinder.find(quad);
        }

        try {
            TextureManager textureManager =
                Minecraft.getInstance()
                    .getTextureManager();

            AbstractTexture texture =
                textureManager.getTexture(
                    PagesOfAtlasRegistry
                        .physicalAtlasLocation(
                            TextureAtlas.LOCATION_BLOCKS,
                            page
                        )
                );

            if (!(texture instanceof TextureAtlas atlas)) {
                return originalFinder.find(quad);
            }

            SpriteFinder pageFinder =
                ((FabricTextureAtlas)atlas)
                    .spriteFinder();

            return pageFinder.find(quad);

        } catch (Throwable ignored) {
            /*
             * Compatibility fallback:
             * never break Continuity just because a physical
             * page finder was unavailable.
             */
            return originalFinder.find(quad);
        }
    }
}
