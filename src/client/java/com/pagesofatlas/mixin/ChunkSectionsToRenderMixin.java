package com.pagesofatlas.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import com.pagesofatlas.PagesOfAtlasRenderPipelines;
import com.pagesofatlas.PagesOfAtlasRegistry;
import com.pagesofatlas.PagesOfAtlasVirtualAtlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderMixin {

    @Redirect(
        method = "renderOit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/oit/OitPipelineSet;getPipeline(Lnet/minecraft/client/renderer/oit/OitStage;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"
        )
    )
    private RenderPipeline pagesofatlas$oitPipeline(
        OitPipelineSet original,
        OitStage stage
    ) {
        boolean splitActive =
            PagesOfAtlasRegistry
                .plan(TextureAtlas.LOCATION_BLOCKS)
                .map(plan -> plan.pageCount() > 1)
                .orElse(false);

        if (!splitActive) {
            return original.getPipeline(stage);
        }

        boolean multiDraw =
            original == RenderPipelines.OIT_TERRAIN_MULTIDRAW;

        OitPipelineSet replacement;

        if (PagesOfAtlasVirtualAtlas.active()) {
            replacement =
                multiDraw
                    ? PagesOfAtlasRenderPipelines
                        .VIRTUAL_OIT_TERRAIN_MULTIDRAW
                    : PagesOfAtlasRenderPipelines
                        .VIRTUAL_OIT_TERRAIN;
        } else {
            replacement =
                multiDraw
                    ? PagesOfAtlasRenderPipelines
                        .OIT_TERRAIN_MULTIDRAW
                    : PagesOfAtlasRenderPipelines
                        .OIT_TERRAIN;
        }

        return replacement.getPipeline(stage);
    }

    @Redirect(
        method = "renderLayers",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;)V",
            ordinal = 0
        )
    )
    private void pagesofatlas$bindPages(
        RenderPass renderPass,
        String name,
        GpuTextureView pageZero,
        GpuSampler sampler
    ) {
        /*
         * Vanilla block atlas.
         */
        renderPass.setUniform(
            name,
            pageZero,
            sampler
        );

        var planOptional =
            PagesOfAtlasRegistry.plan(
                TextureAtlas.LOCATION_BLOCKS
            );

        if (planOptional.isEmpty()) {
            return;
        }

        var plan =
            planOptional.get();

        if (plan.pageCount() < 2) {
            return;
        }

        TextureManager textureManager =
            Minecraft.getInstance()
                .getTextureManager();

        /*
         * Terrain sampler map:
         *
         * page 1 -> Sampler1
         * page 2 -> Sampler3
         * page 3 -> Sampler4
         *
         * Sampler2 remains Minecraft's lightmap.
         *
         * Unused sampler slots receive page 0 so the pipeline
         * always has valid bindings.
         */
        pagesofatlas$bindTerrainPage(
            renderPass,
            textureManager,
            plan,
            1,
            "Sampler1",
            pageZero,
            sampler
        );

        pagesofatlas$bindTerrainPage(
            renderPass,
            textureManager,
            plan,
            2,
            "Sampler3",
            pageZero,
            sampler
        );

        pagesofatlas$bindTerrainPage(
            renderPass,
            textureManager,
            plan,
            3,
            "Sampler4",
            pageZero,
            sampler
        );
    }

    private static void pagesofatlas$bindTerrainPage(
        RenderPass renderPass,
        TextureManager textureManager,
        PagesOfAtlasRegistry.AtlasPlan plan,
        int pageNumber,
        String samplerName,
        GpuTextureView fallback,
        GpuSampler sampler
    ) {
        if (pageNumber >= plan.pageCount()) {
            renderPass.setUniform(
                samplerName,
                fallback,
                sampler
            );

            return;
        }

        AbstractTexture pageTexture =
            textureManager.getTexture(
                PagesOfAtlasRegistry
                    .physicalAtlasLocation(
                        TextureAtlas.LOCATION_BLOCKS,
                        pageNumber
                    )
            );

        renderPass.setUniform(
            samplerName,
            pageTexture.getTextureView(),
            sampler
        );
    }
}
