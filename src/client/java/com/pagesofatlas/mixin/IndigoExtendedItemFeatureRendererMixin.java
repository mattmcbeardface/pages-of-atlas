package com.pagesofatlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.pagesofatlas.PagesOfAtlasItemRendering;

import net.minecraft.client.renderer.rendertype.RenderType;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;

/**
 * Indigo's extended item path already receives PoA's final UV encoding from
 * IndigoMutableQuadViewMixin. Route its block-item draw through the matching
 * page-aware pipeline as well.
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
        RenderType original
    ) {
        return PagesOfAtlasItemRendering.pageAware(
            original
        );
    }
}
