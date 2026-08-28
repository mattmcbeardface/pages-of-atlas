package com.pagesofatlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes each vanilla block-item quad through the RenderType which binds its
 * physical PoA atlas page as Sampler0. UVs remain page-local.
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

    @ModifyExpressionValue(
        method = "prepareMainSubmit",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/resources/model/geometry/" +
                "BakedQuad$MaterialInfo;itemRenderType()" +
                "Lnet/minecraft/client/renderer/rendertype/RenderType;"
        )
    )
    private RenderType pagesofatlas$selectItemRenderType(
        RenderType original,
        @Local BakedQuad.MaterialInfo material
    ) {
        TextureAtlasSprite sprite =
            material.sprite();

        int page =
            sprite instanceof PagedSprite paged
                ? paged.pagesofatlas$getPage()
                : 0;

        return PagesOfAtlasItemRendering.pageAware(
            original,
            page
        );
    }
}
