package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasIrisPbrCompat;
import com.pagesofatlas.PagesOfAtlasPbrDemand;
import com.pagesofatlas.PagesOfAtlasPbrPages;
import com.pagesofatlas.PagesOfAtlasRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs deferred PagesOfAtlas GPU texture work at a RenderPearl-safe
 * frame boundary.
 *
 * Minecraft 26.3 passes an externally-owned RenderPass into Sodium's
 * terrain renderer, so returning from DefaultChunkRenderer.render()
 * no longer means that the active render pass has closed.
 *
 * Minecraft.renderFrame() calls GameRenderer.render() only after its
 * pre-frame GPU task processing has completed. Immediately before
 * GameRenderer.render() therefore provides a point where no world
 * RenderPass is active and CommandEncoder texture writes are legal.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {

    @Inject(
        method = "renderFrame",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/GameRenderer;render()V",
            shift = At.Shift.BEFORE
        )
    )
    private void pagesofatlas$buildDeferredPbrPages(
        boolean advanceGameTime,
        CallbackInfo ci
    ) {
        boolean splitActive =
            PagesOfAtlasRegistry
                .plan(TextureAtlas.LOCATION_BLOCKS)
                .map(plan -> plan.pageCount() > 1)
                .orElse(false);

        if (!splitActive) {
            return;
        }

        if (PagesOfAtlasPbrDemand.consumeClearRequested()) {
            PagesOfAtlasPbrPages.clear();
            PagesOfAtlasIrisPbrCompat.clear();
        }

        if (!PagesOfAtlasPbrDemand.required()) {
            return;
        }

        PagesOfAtlasPbrPages.buildRequestedPages();
    }
}
