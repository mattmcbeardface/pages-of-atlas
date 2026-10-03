package com.pagesofatlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.PagesOfAtlasQuadTag;
import com.pagesofatlas.PagesOfAtlasVirtualAtlas;

import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;

import net.minecraft.client.renderer.rendertype.RenderType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * In virtual mode, Indigo keeps logical UVs through all model transforms.
 * Select the physical page from those final UVs and localize them only while
 * this item quad is buffered. The tag/stride path remains for legacy mode.
 */
@Mixin(
    targets =
        "net.fabricmc.fabric.impl.client.indigo.renderer.render." +
        "ExtendedItemFeatureRenderer",
    remap = false
)
public abstract class IndigoExtendedItemFeatureRendererMixin {

    @Unique
    private int pagesofatlas$currentItemPage;

    @Unique
    private boolean pagesofatlas$virtualItemRoute;

    @Unique
    private final PagesOfAtlasItemRendering.VirtualPageLocalConsumer
        pagesofatlas$virtualUvConsumer =
            new PagesOfAtlasItemRendering.VirtualPageLocalConsumer();

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
        int page =
            PagesOfAtlasVirtualAtlas.active()
                ? PagesOfAtlasItemRendering.virtualPage(
                    quad
                )
                : pagesofatlas$page(quad);

        RenderType selected =
            PagesOfAtlasItemRendering.pageAware(
                original,
                page
            );

        pagesofatlas$currentItemPage = page;
        pagesofatlas$virtualItemRoute =
            PagesOfAtlasVirtualAtlas.active()
                && selected != original;

        return selected;
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
        if (pagesofatlas$virtualItemRoute) {
            return pagesofatlas$virtualUvConsumer.wrap(
                original,
                pagesofatlas$currentItemPage
            );
        }

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
