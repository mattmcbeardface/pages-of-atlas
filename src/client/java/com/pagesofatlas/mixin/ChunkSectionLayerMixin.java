package com.pagesofatlas.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

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
        boolean multiDraw,
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
            PagesOfAtlasVirtualAtlas.active();

        switch (self) {
            case SOLID ->
                cir.setReturnValue(
                    virtual
                        ? (multiDraw
                            ? PagesOfAtlasRenderPipelines.VIRTUAL_SOLID_MULTIDRAW
                            : PagesOfAtlasRenderPipelines.VIRTUAL_SOLID)
                        : (multiDraw
                            ? PagesOfAtlasRenderPipelines.SOLID_MULTIDRAW
                            : PagesOfAtlasRenderPipelines.SOLID)
                );

            case CUTOUT ->
                cir.setReturnValue(
                    virtual
                        ? (multiDraw
                            ? PagesOfAtlasRenderPipelines.VIRTUAL_CUTOUT_MULTIDRAW
                            : PagesOfAtlasRenderPipelines.VIRTUAL_CUTOUT)
                        : (multiDraw
                            ? PagesOfAtlasRenderPipelines.CUTOUT_MULTIDRAW
                            : PagesOfAtlasRenderPipelines.CUTOUT)
                );

            case TRANSLUCENT ->
                cir.setReturnValue(
                    virtual
                        ? (multiDraw
                            ? PagesOfAtlasRenderPipelines.VIRTUAL_TRANSLUCENT_MULTIDRAW
                            : PagesOfAtlasRenderPipelines.VIRTUAL_TRANSLUCENT)
                        : (multiDraw
                            ? PagesOfAtlasRenderPipelines.TRANSLUCENT_MULTIDRAW
                            : PagesOfAtlasRenderPipelines.TRANSLUCENT)
                );
        }
    }
}
