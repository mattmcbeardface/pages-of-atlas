package com.pagesofatlas.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import com.pagesofatlas.PagesOfAtlasRegistry;
import com.pagesofatlas.PagesOfAtlasRenderPipelines;
import com.pagesofatlas.PagesOfAtlasVirtualAtlas;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSectionLayer.class)
public abstract class ChunkSectionLayerMixin {

    @Inject(
        method = "pipeline",
        at = @At("RETURN"),
        cancellable = true
    )
    private void pagesofatlas$pipeline(
        CallbackInfoReturnable<RenderPipeline> cir
    ) {
        boolean splitActive =
            PagesOfAtlasRegistry
                .plan(TextureAtlas.LOCATION_BLOCKS)
                .map(plan ->
                    plan.pageCount() > 1
                )
                .orElse(false);

        if (!splitActive) {
            return;
        }

        ChunkSectionLayer self =
            (ChunkSectionLayer)(Object)this;

        boolean virtual =
            PagesOfAtlasVirtualAtlas.enabled();

        switch (self) {
            case SOLID ->
                cir.setReturnValue(
                    virtual
                        ? PagesOfAtlasRenderPipelines.VIRTUAL_SOLID
                        : PagesOfAtlasRenderPipelines.SOLID
                );

            case CUTOUT ->
                cir.setReturnValue(
                    virtual
                        ? PagesOfAtlasRenderPipelines.VIRTUAL_CUTOUT
                        : PagesOfAtlasRenderPipelines.CUTOUT
                );

            case TRANSLUCENT ->
                cir.setReturnValue(
                    virtual
                        ? PagesOfAtlasRenderPipelines.VIRTUAL_TRANSLUCENT
                        : PagesOfAtlasRenderPipelines.TRANSLUCENT
                );
        }
    }
}
