package com.pagesofatlas.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/*
 * PagesOfAtlas block-item compatibility.
 *
 * Minecraft 26.2 renders ordinary inventory/hand item quads
 * through ItemFeatureRenderer, NOT BlockModelFeatureRenderer.
 *
 * PagesOfAtlas item shaders select the physical atlas page from
 * an encoded U range:
 *
 * page 0 -> normal U
 * page 1 -> U + 2
 * page 2 -> U + 4
 * page 3 -> U + 6
 *
 * Terrain/Sodium must never use this encoding because Sodium
 * compresses terrain UVs separately.
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

    /*
     * Baked block-item quads normally select one of the two singleton
     * block-item RenderTypes in Sheets. Those vanilla render types bind only
     * page zero and use the vanilla item shader, so merely encoding the page
     * in UV0 is not enough. Route exactly those two types through PoA's
     * existing item split pipeline while a split block atlas is active.
     */
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
        RenderType original
    ) {
        return PagesOfAtlasItemRendering.pageAware(
            original
        );
    }

    @Redirect(
        method = "prepareMainSubmit",
        at = @At(
            value = "INVOKE",
            target =
                "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(" +
                "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;" +
                "Lnet/minecraft/client/resources/model/geometry/BakedQuad;" +
                "Lcom/mojang/blaze3d/vertex/QuadInstance;)V"
        )
    )
    private void pagesofatlas$encodeMainItemPage(
        VertexConsumer original,
        PoseStack.Pose pose,
        BakedQuad quad,
        QuadInstance instance
    ) {
        pagesofatlas$putPagedQuad(
            original,
            pose,
            quad,
            instance
        );
    }

    private static void pagesofatlas$putPagedQuad(
        VertexConsumer original,
        PoseStack.Pose pose,
        BakedQuad quad,
        QuadInstance instance
    ) {
        TextureAtlasSprite sprite =
            quad.materialInfo().sprite();

        int page = 0;

        if (sprite instanceof PagedSprite paged) {
            page =
                paged.pagesofatlas$getPage();
        }

        VertexConsumer wrapped =
            PagesOfAtlasItemRendering.pageAware(
                original,
                page
            );

        wrapped.putBakedQuad(
            pose,
            quad,
            instance
        );
    }
}
