package com.pagesofatlas.mixin;

import java.util.function.Function;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.pagesofatlas.PagesOfAtlasItemRendering;
import com.pagesofatlas.PagesOfAtlasRegistry;
import com.pagesofatlas.PagesOfAtlasVirtualAtlas;
import com.pagesofatlas.compat.SodiumExtendedBlockModelPageContext;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Sodium/FRAPI's ExtendedBlockModelFeatureRenderer BufferCache aware
 * of PoA physical pages for block-atlas entity-solid-z-offset submissions.
 *
 * Item frames use this path in Fabric's mesh renderer. The normal Sodium
 * cache keys only on ChunkSectionLayer, so page changes must invalidate that
 * cache entry even when the layer itself does not change.
 */
@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.frapi.render." +
        "ExtendedBlockModelFeatureRenderer$BufferCache",
    remap = false
)
public abstract class SodiumExtendedBlockModelBufferCacheMixin {

    @Shadow
    private ChunkSectionLayer lastLayer;

    @Unique
    private int pagesofatlas$lastPage =
        Integer.MIN_VALUE;

    @Unique
    private boolean pagesofatlas$pageRouted;

    @Unique
    private final PagesOfAtlasItemRendering.VirtualPageLocalConsumer
        pagesofatlas$virtualUvConsumer =
            new PagesOfAtlasItemRendering.VirtualPageLocalConsumer();

    @Inject(
        method = "prepare",
        at = @At("HEAD"),
        remap = false
    )
    private void pagesofatlas$resetPageCache(
        Function<ChunkSectionLayer, RenderType> renderTypeFunction,
        PoseStack.Pose sheetedDecalPose,
        CallbackInfo ci
    ) {
        pagesofatlas$lastPage =
            Integer.MIN_VALUE;
        pagesofatlas$pageRouted = false;
    }

    @Inject(
        method = "getBuffer",
        at = @At("HEAD"),
        remap = false
    )
    private void pagesofatlas$invalidateOnPageChange(
        ChunkSectionLayer layer,
        CallbackInfoReturnable<VertexConsumer> cir
    ) {
        int page =
            SodiumExtendedBlockModelPageContext.get();

        if (page != pagesofatlas$lastPage) {
            /*
             * Sodium normally caches only by ChunkSectionLayer. Force its
             * normal lookup path to run again when the physical atlas page
             * changes while the layer remains the same.
             */
            lastLayer = null;

            pagesofatlas$lastPage = page;
            pagesofatlas$pageRouted = false;
        }
    }

    @ModifyExpressionValue(
        method = "getBuffer",
        at = @At(
            value = "INVOKE",
            target =
                "Ljava/util/function/Function;apply(" +
                "Ljava/lang/Object;)Ljava/lang/Object;"
        ),
        remap = false
    )
    private Object pagesofatlas$selectPhysicalPage(
        Object original
    ) {
        pagesofatlas$pageRouted = false;

        int page =
            SodiumExtendedBlockModelPageContext.get();

        if (
            !PagesOfAtlasVirtualAtlas.active()
            || page < 0
            || !(original instanceof RenderType originalType)
        ) {
            return original;
        }

        RenderType logicalFrameType =
            RenderTypes.entitySolidZOffsetForward(
                TextureAtlas.LOCATION_BLOCKS
            );

        /*
         * Do not broaden this adapter to arbitrary FRAPI block-model
         * submissions. Item frames reach this renderer through Minecraft's
         * block-atlas entity-solid-z-offset RenderType.
         */
        if (originalType != logicalFrameType) {
            return original;
        }

        Identifier texture =
            page == 0
                ? TextureAtlas.LOCATION_BLOCKS
                : PagesOfAtlasRegistry.physicalAtlasLocation(
                    TextureAtlas.LOCATION_BLOCKS,
                    page
                );

        pagesofatlas$pageRouted = true;

        return RenderTypes.entitySolidZOffsetForward(
            texture
        );
    }

    @Inject(
        method = "getBuffer",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void pagesofatlas$localizeVirtualUv(
        ChunkSectionLayer layer,
        CallbackInfoReturnable<VertexConsumer> cir
    ) {
        if (!pagesofatlas$pageRouted) {
            return;
        }

        int page =
            SodiumExtendedBlockModelPageContext.get();

        VertexConsumer original =
            cir.getReturnValue();

        if (
            page < 0
            || original == null
        ) {
            return;
        }

        /*
         * This is required for page zero as well: its physical texture is
         * 16384x16384 while the logical virtual atlas is 32768x32768.
         */
        cir.setReturnValue(
            pagesofatlas$virtualUvConsumer.wrap(
                original,
                page
            )
        );
    }
}
