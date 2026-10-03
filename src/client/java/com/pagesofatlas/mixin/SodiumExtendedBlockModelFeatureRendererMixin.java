package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.PagesOfAtlasVirtualAtlas;
import com.pagesofatlas.compat.SodiumExtendedBlockModelPageContext;
import com.pagesofatlas.compat.SodiumQuadUvAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the physical PoA page from each final Sodium/FRAPI extended
 * block-model quad. Item frames use this mesh path when Fabric Renderer API
 * replaces vanilla BlockModelRenderState modelParts with a MutableMesh.
 */
@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.frapi.render." +
        "ExtendedBlockModelFeatureRenderer",
    remap = false
)
public abstract class SodiumExtendedBlockModelFeatureRendererMixin {

    @Inject(
        method = "bufferQuad",
        at = @At("HEAD"),
        remap = false
    )
    private void pagesofatlas$capturePage(
        @Coerce Object quad,
        CallbackInfo ci
    ) {
        SodiumExtendedBlockModelPageContext.clear();

        if (
            PagesOfAtlasVirtualAtlas.active()
            && quad instanceof SodiumQuadUvAccess access
        ) {
            SodiumExtendedBlockModelPageContext.set(
                PagesOfAtlasItemRendering.virtualPage(
                    access
                )
            );
        }
    }

    @Inject(
        method = "bufferQuad",
        at = @At("RETURN"),
        remap = false
    )
    private void pagesofatlas$clearPage(
        @Coerce Object quad,
        CallbackInfo ci
    ) {
        SodiumExtendedBlockModelPageContext.clear();
    }
}
