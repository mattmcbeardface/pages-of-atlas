package com.pagesofatlas.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import com.pagesofatlas.PagesOfAtlasPbrPages;
import com.pagesofatlas.PagesOfAtlasRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",
    remap = false
)
public abstract class SodiumDefaultChunkRendererMixin {

    /*
     * Sodium's second bindTexture() invocation is u_BlockTex.
     *
     * Bind diffuse page zero normally, then expose POA diffuse
     * pages 1-3 plus POA-owned PBR page 2.
     */
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target =
                "Lcom/mojang/blaze3d/systems/RenderPass;bindTexture(Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
            ordinal = 1
        ),
        remap = false
    )
    private void pagesofatlas$bindBlockPages(
        RenderPass renderPass,
        String name,
        GpuTextureView pageZero,
        GpuSampler sampler
    ) {
        /*
         * Original Sodium block texture binding.
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

            /*
             * The expanded pipeline still expects every binding.
             *
             * With no active POA atlas, use page zero as a harmless
             * fallback for all additional samplers.
             */
            renderPass.bindTexture(
                "u_BlockNormalTex0",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockSpecularTex0",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockNormalTex1",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockSpecularTex1",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockNormalTex2",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockSpecularTex2",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockNormalTex3",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockSpecularTex3",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockTex1",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockTex2",
                pageZero,
                sampler
            );

            renderPass.bindTexture(
                "u_BlockTex3",
                pageZero,
                sampler
            );

            return;
        }

        var plan =
            planOptional.get();

        TextureManager textureManager =
            Minecraft.getInstance()
                .getTextureManager();

        /*
         * Diffuse physical pages.
         */
        pagesofatlas$bindPage(
            renderPass,
            textureManager,
            plan,
            1,
            "u_BlockTex1",
            pageZero,
            sampler
        );

        pagesofatlas$bindPage(
            renderPass,
            textureManager,
            plan,
            2,
            "u_BlockTex2",
            pageZero,
            sampler
        );

        pagesofatlas$bindPage(
            renderPass,
            textureManager,
            plan,
            3,
            "u_BlockTex3",
            pageZero,
            sampler
        );

        /*
         * =========================================================
         * DETERMINISTIC PBR PAGES 0-3
         * =========================================================
         *
         * Every physical POA diffuse page receives matching normal
         * and specular companions built from the SAME authoritative
         * placement map.
         *
         * We only REQUEST pages while Sodium's RenderPass is active.
         * The @RETURN injection below performs the actual builds
         * after RenderPass.close().
         */
        /*
         * DIAGNOSTIC BUILD:
         *
         * Do not request POA-owned full-resolution PBR pages.
         *
         * Shader-side PBR interception is disabled in this build, so
         * constructing eight 16384x16384 normal/specular textures
         * would consume VRAM without contributing to rendering.
         *
         * Keep the sampler bindings below intact; with no allocated
         * POA PBR page they safely receive the ordinary fallback view.
         */
        // PBR page requests intentionally disabled.

        pagesofatlas$bindPbrPage(
            renderPass,
            0,
            "u_BlockNormalTex0",
            "u_BlockSpecularTex0",
            pageZero,
            sampler
        );

        pagesofatlas$bindPbrPage(
            renderPass,
            1,
            "u_BlockNormalTex1",
            "u_BlockSpecularTex1",
            pageZero,
            sampler
        );

        pagesofatlas$bindPbrPage(
            renderPass,
            2,
            "u_BlockNormalTex2",
            "u_BlockSpecularTex2",
            pageZero,
            sampler
        );

        pagesofatlas$bindPbrPage(
            renderPass,
            3,
            "u_BlockNormalTex3",
            "u_BlockSpecularTex3",
            pageZero,
            sampler
        );
    }

    /*
     * Sodium's bytecode closes the terrain RenderPass before render()
     * returns.
     *
     * This is therefore the safe point to perform large GPU texture
     * uploads requested during the frame.
     */
    @Inject(
        method = "render",
        at = @At("RETURN"),
        remap = false
    )
    private void pagesofatlas$buildRequestedPbrPages(
        CallbackInfo ci
    ) {
        /*
         * DIAGNOSTIC BUILD:
         * Full-resolution POA PBR page construction is disabled.
         */
        // PagesOfAtlasPbrPages.buildRequestedPages();
    }

    private static void pagesofatlas$bindPbrPage(
        RenderPass renderPass,
        int page,
        String normalSamplerName,
        String specularSamplerName,
        GpuTextureView fallback,
        GpuSampler sampler
    ) {
        GpuTextureView normal =
            PagesOfAtlasPbrPages.existingNormalPage(
                page
            );

        GpuTextureView specular =
            PagesOfAtlasPbrPages.existingSpecularPage(
                page
            );

        renderPass.bindTexture(
            normalSamplerName,
            normal != null
                ? normal
                : fallback,
            sampler
        );

        renderPass.bindTexture(
            specularSamplerName,
            specular != null
                ? specular
                : fallback,
            sampler
        );
    }

    private static void pagesofatlas$bindPage(
        RenderPass renderPass,
        TextureManager textureManager,
        PagesOfAtlasRegistry.AtlasPlan plan,
        int page,
        String samplerName,
        GpuTextureView fallback,
        GpuSampler sampler
    ) {
        var pageOptional =
            plan.page(page);

        if (pageOptional.isEmpty()) {
            renderPass.bindTexture(
                samplerName,
                fallback,
                sampler
            );

            return;
        }

        AbstractTexture texture =
            textureManager.getTexture(
                pageOptional.get()
                    .physicalAtlas()
            );

        if (
            texture == null
            || texture.getTextureView() == null
        ) {
            renderPass.bindTexture(
                samplerName,
                fallback,
                sampler
            );

            return;
        }

        renderPass.bindTexture(
            samplerName,
            texture.getTextureView(),
            sampler
        );
    }
}
