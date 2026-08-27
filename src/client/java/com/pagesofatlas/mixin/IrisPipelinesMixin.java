package com.pagesofatlas.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.pagesofatlas.PagesOfAtlasRenderPipelines;

import net.minecraft.client.renderer.RenderPipelines;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers POA's block-item pipelines with Iris's own semantic
 * RenderPipeline lookup.  Iris deliberately keys this lookup by the
 * RenderPipeline object, so independently registered pipelines do not
 * inherit an override merely because they use the same vertex format or
 * shaders as a vanilla pipeline.
 */
@Pseudo
@Mixin(
    targets = "net.irisshaders.iris.pipeline.IrisPipelines",
    remap = false
)
public abstract class IrisPipelinesMixin {

    @Shadow(remap = false)
    public static void copyPipeline(
        RenderPipeline pipeline,
        RenderPipeline pipelineToCopy
    ) {
        throw new AssertionError();
    }

    @Inject(
        method = "<clinit>",
        at = @At("TAIL"),
        remap = false
    )
    private static void pagesofatlas$registerItemPipelines(
        CallbackInfo ci
    ) {
        copyPipeline(
            RenderPipelines.ITEM_CUTOUT,
            PagesOfAtlasRenderPipelines.ITEM_CUTOUT
        );

        copyPipeline(
            RenderPipelines.ITEM_TRANSLUCENT,
            PagesOfAtlasRenderPipelines.ITEM_TRANSLUCENT
        );
    }
}
