package com.pagesofatlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.PagesOfAtlasVirtualAtlas;
import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes each vanilla block-item quad through the RenderType which binds its
 * physical PoA atlas page as Sampler0. Virtual mode derives that page from
 * the final quad UVs and converts those UVs to page-local values at emission.
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

    @Unique
    private int pagesofatlas$currentItemPage;

    @Unique
    private boolean pagesofatlas$virtualItemRoute;

    @Unique
    private final PagesOfAtlasItemRendering.VirtualPageLocalConsumer
        pagesofatlas$virtualUvConsumer =
            new PagesOfAtlasItemRendering.VirtualPageLocalConsumer();

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
        @Local BakedQuad quad,
        @Local BakedQuad.MaterialInfo material
    ) {
        int page;

        if (PagesOfAtlasVirtualAtlas.enabled()) {
            page =
                PagesOfAtlasItemRendering.virtualPage(
                    quad
                );
        } else {
            TextureAtlasSprite sprite =
                material.sprite();

            page =
                sprite instanceof PagedSprite paged
                    ? paged.pagesofatlas$getPage()
                    : 0;
        }

        RenderType selected =
            PagesOfAtlasItemRendering.pageAware(
                original,
                page
            );

        pagesofatlas$currentItemPage = page;
        pagesofatlas$virtualItemRoute =
            PagesOfAtlasVirtualAtlas.enabled()
                && selected != original;

        return selected;
    }

    @ModifyExpressionValue(
        method = "prepareMainSubmit",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/feature/" +
                "ItemFeatureRenderer;getVertexBuilder(" +
                "Lnet/minecraft/client/renderer/rendertype/RenderType;)" +
                "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
        )
    )
    private VertexConsumer pagesofatlas$pageLocalVirtualUv(
        VertexConsumer original
    ) {
        if (!pagesofatlas$virtualItemRoute) {
            return original;
        }

        return pagesofatlas$virtualUvConsumer.wrap(
            original,
            pagesofatlas$currentItemPage
        );
    }
}
