package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasPbrDemand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets =
        "net.irisshaders.iris.pipeline.IrisRenderingPipeline",
    remap = false
)
public abstract class IrisRenderingPipelineMixin {

    @Shadow(remap = false)
    private boolean shouldBindPBR;

    /*
     * Iris computes shouldBindPBR while constructing the rendering
     * pipeline by checking whether its compiled programs actually
     * contain "normals" or "specular" samplers.
     *
     * At constructor return this is therefore Iris's authoritative
     * result for the newly built pipeline.
     */
    @Inject(
        method = "<init>",
        at = @At("RETURN"),
        remap = false
    )
    private void pagesofatlas$capturePbrDemand(
        CallbackInfo ci
    ) {
        PagesOfAtlasPbrDemand.setRequired(
            this.shouldBindPBR
        );
    }
}
