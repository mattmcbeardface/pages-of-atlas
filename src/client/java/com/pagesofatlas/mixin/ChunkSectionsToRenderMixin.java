package com.pagesofatlas.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import com.pagesofatlas.PagesOfAtlasRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderMixin {

    @Redirect(
        method = "renderGroup",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;bindTexture(Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
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
        renderPass.bindTexture(
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
            renderPass.bindTexture(
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

        renderPass.bindTexture(
            samplerName,
            pageTexture.getTextureView(),
            sampler
        );
    }
}
