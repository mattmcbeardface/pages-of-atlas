package com.pagesofatlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.PagesOfAtlasQuadTag;
import com.pagesofatlas.compat.SodiumQuadTagAccess;

import net.minecraft.client.renderer.rendertype.RenderType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium/FRAPI's ExtendedItemFeatureRenderer is a second item-rendering path
 * which does not enter Minecraft's ItemFeatureRenderer. The existing Sodium
 * quad tag carries PoA's page through model transforms; consume that same tag
 * when Sodium finally buffers an item quad.
 */
@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.frapi.render." +
        "ExtendedItemFeatureRenderer",
    remap = false
)
public abstract class SodiumExtendedItemFeatureRendererMixin {

    @Unique
    private static final ThreadLocal<Integer>
        pagesofatlas$currentItemPage =
            ThreadLocal.withInitial(() -> 0);

    @Inject(
        method = "bufferMain",
        at = @At("HEAD"),
        remap = false
    )
    private void pagesofatlas$captureItemPage(
        @Coerce Object quad,
        CallbackInfo ci
    ) {
        int page = 0;

        if (quad instanceof SodiumQuadTagAccess access) {
            int tag =
                access.pagesofatlas$getSodiumTag();

            if (PagesOfAtlasQuadTag.isPagesOfAtlasTag(tag)) {
                page =
                    PagesOfAtlasQuadTag.page(tag);
            }
        }

        pagesofatlas$currentItemPage.set(
            page
        );
    }

    @ModifyExpressionValue(
        method = "bufferMain",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/caffeinemc/mods/sodium/client/render/frapi/" +
                "wrapper/MutableQuadViewWrapper;itemRenderType()" +
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

    @ModifyArg(
        method = "bufferMain",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/caffeinemc/mods/sodium/client/render/frapi/" +
                "wrapper/MutableQuadViewWrapper;buffer(" +
                "ILcom/mojang/blaze3d/vertex/PoseStack$Pose;" +
                "Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"
        ),
        index = 2,
        remap = false
    )
    private VertexConsumer pagesofatlas$encodeItemPage(
        VertexConsumer original
    ) {
        return PagesOfAtlasItemRendering.pageAware(
            original,
            pagesofatlas$currentItemPage.get()
        );
    }

    @Inject(
        method = "bufferMain",
        at = @At("RETURN"),
        remap = false
    )
    private void pagesofatlas$clearItemPage(
        @Coerce Object quad,
        CallbackInfo ci
    ) {
        pagesofatlas$currentItemPage.remove();
    }
}
