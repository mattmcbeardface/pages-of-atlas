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
             * True passthrough.
             *
             * The normal Sodium block texture was already bound
             * above. With no active POA split atlas, do not add any
             * POA sampler bindings to the render pass.
             */
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
         * Request deterministic full-resolution PBR companions.
         *
         * They are built after the active RenderPass closes.
         */
        /*
         * Stage expensive PBR construction across terrain renders.
         *
         * Request only the first physical page whose PBR companions
         * do not exist yet. The @RETURN injection will build that one
         * page after this RenderPass closes.
         *
         * Subsequent terrain renders progressively request the
         * remaining pages instead of constructing all four in one
         * enormous startup burst.
         */
        for (int page = 1; page < 4; page++) {
            if (
                PagesOfAtlasPbrPages.existingNormalPage(
                    page
                ) == null
                || PagesOfAtlasPbrPages.existingSpecularPage(
                    page
                ) == null
            ) {
                PagesOfAtlasPbrPages.requestPage(
                    page
                );

                break;
            }
        }

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

        PagesOfAtlasPbrPages.buildRequestedPages();
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
