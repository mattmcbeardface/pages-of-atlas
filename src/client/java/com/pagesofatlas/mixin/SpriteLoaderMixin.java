package com.pagesofatlas.mixin;

import com.pagesofatlas.PagedTextureAtlasSprite;
import com.pagesofatlas.PagesOfAtlasClient;
import com.pagesofatlas.PagesOfAtlasPager;
import com.pagesofatlas.PagesOfAtlasRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {

    @Shadow
    @Final
    private Identifier location;

    @Shadow
    @Final
    private int maxSupportedTextureSize;

    @Inject(
        method = "stitch",
        at = @At("HEAD"),
        cancellable = true
    )
    private void pagesofatlas$stitch(
        List<SpriteContents> sprites,
        int maxMipmapLevels,
        Executor executor,
        CallbackInfoReturnable<SpriteLoader.Preparations> cir
    ) {
        PagesOfAtlasRegistry.beginAtlas(
            location
        );

        /*
         * Capture the logical sprite dimensions at stitch time.
         *
         * This is the authoritative source for future PBR companion
         * construction. We deliberately record dimensions here rather
         * than querying AtlasManager later during resource reload.
         */
        for (SpriteContents sprite : sprites) {
            PagesOfAtlasRegistry.recordSpriteDimensions(
                location,
                sprite.name(),
                sprite.width(),
                sprite.height()
            );
        }


        int minTexelSize =
            Integer.MAX_VALUE;

        int lowestOneBit =
            1 << maxMipmapLevels;

        for (SpriteContents sprite :
            sprites) {

            minTexelSize =
                Math.min(
                    minTexelSize,
                    Math.min(
                        sprite.width(),
                        sprite.height()
                    )
                );

            int lowestTextureBit =
                Math.min(
                    Integer.lowestOneBit(
                        sprite.width()
                    ),
                    Integer.lowestOneBit(
                        sprite.height()
                    )
                );

            if (lowestTextureBit <
                lowestOneBit) {

                lowestOneBit =
                    lowestTextureBit;
            }
        }

        int minSize =
            Math.min(
                minTexelSize,
                lowestOneBit
            );

        int minPowerOfTwo =
            Mth.log2(minSize);

        int mipLevel;

        if (minPowerOfTwo <
            maxMipmapLevels) {

            mipLevel =
                minPowerOfTwo;

        } else {
            mipLevel =
                maxMipmapLevels;
        }

        Options options =
            Minecraft.getInstance().options;

        int anisotropyBit =
            options.textureFiltering().get()
                != TextureFilteringMethod.ANISOTROPIC
                ? 0
                : options.maxAnisotropyBit()
                    .get();

        int padding =
            (1 << mipLevel)
                << Mth.clamp(
                    anisotropyBit - 1,
                    0,
                    4
                );

        PagesOfAtlasPager.Result<SpriteContents>
            result;

        try {
            result =
                PagesOfAtlasPager.pack(
                    sprites,
                    maxSupportedTextureSize,
                    maxSupportedTextureSize,
                    mipLevel,
                    padding
                );
        } catch (Throwable t) {
            PagesOfAtlasRegistry.endAtlas();

            PagesOfAtlasClient.LOGGER.error(
                "PagesOfAtlas preflight failed for {}",
                location,
                t
            );

            return;
        }

        /*
         * A one-page atlas does not need us.
         * Let vanilla perform its normal stitch.
         */
        if (result.pages().size() <= 1) {
            PagesOfAtlasRegistry.endAtlas();
            return;
        }

        /*
         * The current PagesOfAtlas shader layout supports
         * four physical block-atlas pages.
         *
         * Fail explicitly rather than silently rendering
         * pages beyond the available GPU sampler bindings.
         */
        if (
            result.pages().size()
                > com.pagesofatlas.PagesOfAtlasRenderPipelines.MAX_BLOCK_PAGES
        ) {
            PagesOfAtlasRegistry.endAtlas();

            throw new IllegalStateException(
                "PagesOfAtlas currently supports at most "
                    + com.pagesofatlas.PagesOfAtlasRenderPipelines.MAX_BLOCK_PAGES
                    + " atlas pages; "
                    + location
                    + " requires "
                    + result.pages().size()
            );
        }

        /*
         * This atlas really needs paging.
         */
        PagesOfAtlasRegistry.publishCurrent(
            result
        );

        Map<Identifier, TextureAtlasSprite>
            combinedRegions =
                new HashMap<>();

        List<PagesOfAtlasRegistry.PageUpload>
            pageUploads =
                new java.util.ArrayList<>();

        TextureAtlasSprite logicalMissing =
            null;

        for (PagesOfAtlasPager.Page<SpriteContents>
            page : result.pages()) {

            Map<Identifier, TextureAtlasSprite>
                pageRegions =
                    new HashMap<>();

            TextureAtlasSprite pageMissing =
                null;

            TextureAtlasSprite firstSprite =
                null;

            for (
                PagesOfAtlasPager.Placement<SpriteContents>
                    placement :
                page.placements()
            ) {
                SpriteContents contents =
                    placement.entry();

                PagedTextureAtlasSprite sprite =
                    new PagedTextureAtlasSprite(
                        location,
                        contents,
                        page.width(),
                        page.height(),
                        placement.x(),
                        placement.y(),
                        placement.padding(),
                        page.number()
                    );

                if (firstSprite == null) {
                    firstSprite =
                        sprite;
                }

                combinedRegions.put(
                    contents.name(),
                    sprite
                );

                pageRegions.put(
                    contents.name(),
                    sprite
                );

                if (
                    contents.name().equals(
                        MissingTextureAtlasSprite
                            .getLocation()
                    )
                ) {
                    pageMissing =
                        sprite;

                    logicalMissing =
                        sprite;
                }
            }

            /*
             * TextureAtlas.upload() requires every physical
             * atlas to have a "missing" entry.
             *
             * For secondary physical pages we only use the
             * atlas as a GPU texture, not for sprite lookup.
             * If missingno was packed on another page, alias
             * one existing sprite under the missingno key.
             *
             * We remove its original page-map key first so
             * TextureAtlas only uploads that object once.
             */
            if (pageMissing == null &&
                firstSprite != null) {

                pageRegions.remove(
                    firstSprite.contents()
                        .name()
                );

                pageRegions.put(
                    MissingTextureAtlasSprite
                        .getLocation(),
                    firstSprite
                );

                pageMissing =
                    firstSprite;
            }

            CompletableFuture<Void>
                pageReady =
                    CompletableFuture.completedFuture(
                        null
                    );

            SpriteLoader.Preparations
                pagePreparation =
                    new SpriteLoader.Preparations(
                        page.width(),
                        page.height(),
                        mipLevel,
                        pageMissing,
                        Map.copyOf(
                            pageRegions
                        ),
                        pageReady
                    );

            pageUploads.add(
                new PagesOfAtlasRegistry.PageUpload(
                    page.number(),
                    PagesOfAtlasRegistry
                        .physicalAtlasLocation(
                            location,
                            page.number()
                        ),
                    pagePreparation
                )
            );
        }

        if (logicalMissing == null) {
            logicalMissing =
                combinedRegions.get(
                    MissingTextureAtlasSprite
                        .getLocation()
                );
        }

        if (logicalMissing == null &&
            !combinedRegions.isEmpty()) {

            logicalMissing =
                combinedRegions.values()
                    .iterator()
                    .next();
        }

        /*
         * Generate mipmaps exactly once for the original
         * SpriteContents objects.
         */
        CompletableFuture<Void>
            readyForUpload =
                CompletableFuture.runAsync(
                    () -> sprites.forEach(
                        sprite ->
                            sprite.increaseMipLevel(
                                mipLevel
                            )
                    ),
                    executor
                );

        /*
         * Vanilla AtlasManager receives this combined map,
         * so all 3680 logical sprites remain available to
         * model baking regardless of physical page.
         */
        SpriteLoader.Preparations combined =
            new SpriteLoader.Preparations(
                result.pages()
                    .getFirst()
                    .width(),
                result.pages()
                    .getFirst()
                    .height(),
                mipLevel,
                logicalMissing,
                Map.copyOf(
                    combinedRegions
                ),
                readyForUpload
            );

        /*
         * Make every physical page wait for that same mip
         * generation task.
         */
        List<PagesOfAtlasRegistry.PageUpload>
            finalPageUploads =
                pageUploads.stream()
                    .map(
                        upload ->
                            new PagesOfAtlasRegistry.PageUpload(
                                upload.page(),
                                upload.physicalAtlas(),
                                new SpriteLoader.Preparations(
                                    upload.preparations()
                                        .width(),
                                    upload.preparations()
                                        .height(),
                                    mipLevel,
                                    upload.preparations()
                                        .missing(),
                                    upload.preparations()
                                        .regions(),
                                    readyForUpload
                                )
                            )
                    )
                    .toList();

        PagesOfAtlasRegistry.publishUploadBundle(
            location,
            new PagesOfAtlasRegistry.UploadBundle(
                combined,
                finalPageUploads
            )
        );

        cir.setReturnValue(
            combined
        );
    }

    @Inject(
        method = "stitch",
        at = @At("RETURN")
    )
    private void pagesofatlas$finishNormalStitch(
        List<SpriteContents> sprites,
        int maxMipmapLevels,
        Executor executor,
        CallbackInfoReturnable<SpriteLoader.Preparations> cir
    ) {
        PagesOfAtlasRegistry.endAtlas();
    }
}
