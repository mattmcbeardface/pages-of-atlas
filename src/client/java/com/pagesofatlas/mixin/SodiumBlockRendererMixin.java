package com.pagesofatlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.pagesofatlas.PagesOfAtlasQuadTag;
import com.pagesofatlas.compat.SodiumQuadTagAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
    remap = false
)
public abstract class SodiumBlockRendererMixin {

    @Unique
    private static final int
        pagesofatlas$NO_PAGE =
            -1;

    @Unique
    private static final ThreadLocal<int[]>
        pagesofatlas$currentPage =
            ThreadLocal.withInitial(
                () -> new int[] {
                    pagesofatlas$NO_PAGE
                }
            );

    @Inject(
        method = "processQuad",
        at = @At("HEAD"),
        remap = false
    )
    private void pagesofatlas$capturePage(
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

        page &= 0x3;

        pagesofatlas$currentPage.get()[0] =
            page;
    }

    @ModifyExpressionValue(
        method = "bufferQuad",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;bits()I"
        ),
        remap = false
    )
    private int pagesofatlas$encodePageInMaterial(
        int original
    ) {
        int page =
            pagesofatlas$currentPage.get()[0];

        if (page == pagesofatlas$NO_PAGE) {
            page = 0;
        }

        return original
            | ((page & 0x3) << 3);
    }

    @Inject(
        method = "processQuad",
        at = @At("RETURN"),
        remap = false
    )
    private void pagesofatlas$clearPage(
        @Coerce Object quad,
        CallbackInfo ci
    ) {
        pagesofatlas$currentPage.get()[0] =
            pagesofatlas$NO_PAGE;
    }
}
