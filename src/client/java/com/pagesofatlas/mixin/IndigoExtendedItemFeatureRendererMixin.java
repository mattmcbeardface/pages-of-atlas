package com.pagesofatlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.PagesOfAtlasQuadTag;

import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;

import net.minecraft.client.renderer.rendertype.RenderType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Indigo's extended item path retains PoA's page in its quad tag. Select the
 * matching page-bound RenderType, then undo Indigo's terrain-compatible UV
 * page stride only while this item quad is buffered.
 */
@Mixin(
    targets =
        "net.fabricmc.fabric.impl.client.indigo.renderer.render." +
        "ExtendedItemFeatureRenderer",
    remap = false
)
public abstract class IndigoExtendedItemFeatureRendererMixin {

    @ModifyExpressionValue(
        method = "bufferMain",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/" +
                "MutableQuadViewImpl;itemRenderType()" +
                "Lnet/minecraft/client/renderer/rendertype/RenderType;"
        ),
        remap = false
    )
    private RenderType pagesofatlas$selectItemRenderType(
        RenderType original,
        @Local(argsOnly = true) MutableQuadViewImpl quad
    ) {
        return PagesOfAtlasItemRendering.pageAware(
            original,
            pagesofatlas$page(quad)
        );
    }

    @ModifyArg(
        method = "bufferMain",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/" +
                "MutableQuadViewImpl;buffer(" +
                "ILcom/mojang/blaze3d/vertex/PoseStack$Pose;" +
                "Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"
        ),
        index = 2,
        remap = false
    )
    private VertexConsumer pagesofatlas$pageLocalItemUv(
        VertexConsumer original,
        @Local(argsOnly = true) MutableQuadViewImpl quad
    ) {
        return PagesOfAtlasItemRendering.pageLocal(
            original,
            pagesofatlas$page(quad)
        );
    }

    @Unique
    private static int pagesofatlas$page(
        MutableQuadViewImpl quad
    ) {
        int tag =
            quad.tag();

        return PagesOfAtlasQuadTag.isPagesOfAtlasTag(tag)
            ? PagesOfAtlasQuadTag.page(tag)
            : 0;
    }
}
