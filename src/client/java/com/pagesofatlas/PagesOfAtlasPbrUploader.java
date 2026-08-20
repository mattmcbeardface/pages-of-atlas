package com.pagesofatlas;

import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Builds POA-owned PBR companions directly from the authoritative
 * PagesOfAtlas placement map.
 *
 * No independent PBR stitching occurs here.
 */
public final class PagesOfAtlasPbrUploader {

    /*
     * Iris PBRType defaults:
     *
     * NORMAL   = 0x7F7FFFFF
     * SPECULAR = 0x00000000
     */
    private static final int DEFAULT_NORMAL =
        0x7F7FFFFF;

    private static final int DEFAULT_SPECULAR =
        0x00000000;

    private PagesOfAtlasPbrUploader() {}

    public static void uploadPage(
        PagesOfAtlasPbrPage page
    ) throws Exception {

        ResourceManager resources =
            Minecraft.getInstance()
                .getResourceManager();

        var placements =
            PagesOfAtlasRegistry.placementsForPage(
                TextureAtlas.LOCATION_BLOCKS,
                page.page()
            );

        if (placements.isEmpty()) {
            PagesOfAtlasClient.LOGGER.warn(
                "[PBR UPLOAD] Page {} has no POA placements",
                page.page()
            );

            return;
        }

        page.allocate();

        /*
         * Initialize the atlas with Iris-neutral PBR values WITHOUT
         * allocating a full 16K NativeImage.
         *
         * A full 16384x16384 RGBA image is ~1 GiB. Two of them would
         * recreate exactly the transient RAM spike POA is trying to
         * eliminate.
         *
         * Instead, upload a reusable 256-row stripe.
         */
        PagesOfAtlasClient.LOGGER.info(
            "[PBR UPLOAD] Initializing neutral page {} with striped upload",
            page.page()
        );

        final int stripeHeight = 256;

        try (
            NativeImage stripe =
                new NativeImage(
                    page.width(),
                    Math.min(
                        stripeHeight,
                        page.height()
                    ),
                    false
                )
        ) {
            /*
             * NORMAL
             *
             * Batch every neutral normal stripe into one command
             * encoder and submit once for the complete page.
             *
             * Keep both the reusable full stripe and any possible
             * short final stripe alive until submit().
             */
            stripe.fillRect(
                0,
                0,
                stripe.getWidth(),
                stripe.getHeight(),
                DEFAULT_NORMAL
            );

            NativeImage shortNormalStripe = null;

            try {
                CommandEncoder encoder =
                    RenderSystem.getDevice()
                        .createCommandEncoder();

                for (
                    int y = 0;
                    y < page.height();
                    y += stripeHeight
                ) {
                    int remaining =
                        Math.min(
                            stripeHeight,
                            page.height() - y
                        );

                    NativeImage uploadStripe =
                        stripe;

                    /*
                     * Page heights are normally multiples of 256,
                     * but retain safe handling for a short final
                     * stripe.
                     */
                    if (remaining != stripe.getHeight()) {
                        shortNormalStripe =
                            new NativeImage(
                                page.width(),
                                remaining,
                                false
                            );

                        shortNormalStripe.fillRect(
                            0,
                            0,
                            shortNormalStripe.getWidth(),
                            shortNormalStripe.getHeight(),
                            DEFAULT_NORMAL
                        );

                        uploadStripe =
                            shortNormalStripe;
                    }

                    encoder.writeToTexture(
                        page.normalTexture(),
                        uploadStripe,
                        0,
                        0,
                        0,
                        y
                    );
                }

                encoder.submit();

            } finally {
                if (shortNormalStripe != null) {
                    shortNormalStripe.close();
                }
            }

            /*
             * The normal upload has now been submitted, so it is
             * safe to reuse and recolor the stripe for SPECULAR.
             *
             * Again, queue every stripe first and submit only once.
             */
            stripe.fillRect(
                0,
                0,
                stripe.getWidth(),
                stripe.getHeight(),
                DEFAULT_SPECULAR
            );

            NativeImage shortSpecularStripe = null;

            try {
                CommandEncoder encoder =
                    RenderSystem.getDevice()
                        .createCommandEncoder();

                for (
                    int y = 0;
                    y < page.height();
                    y += stripeHeight
                ) {
                    int remaining =
                        Math.min(
                            stripeHeight,
                            page.height() - y
                        );

                    NativeImage uploadStripe =
                        stripe;

                    if (remaining != stripe.getHeight()) {
                        shortSpecularStripe =
                            new NativeImage(
                                page.width(),
                                remaining,
                                false
                            );

                        shortSpecularStripe.fillRect(
                            0,
                            0,
                            shortSpecularStripe.getWidth(),
                            shortSpecularStripe.getHeight(),
                            DEFAULT_SPECULAR
                        );

                        uploadStripe =
                            shortSpecularStripe;
                    }

                    encoder.writeToTexture(
                        page.specularTexture(),
                        uploadStripe,
                        0,
                        0,
                        0,
                        y
                    );
                }

                encoder.submit();

            } finally {
                if (shortSpecularStripe != null) {
                    shortSpecularStripe.close();
                }
            }
        }

        int normalFound = 0;
        int normalUploaded = 0;

        int specularFound = 0;
        int specularUploaded = 0;

        int scaled = 0;
        int failed = 0;

        for (var placed : placements) {

            Identifier sprite =
                placed.sprite();

            var placement =
                placed.placement();

            UploadResult normal =
                uploadOne(
                    resources,
                    page,
                    sprite,
                    placement,
                    PagesOfAtlasPbrSourceResolver.Type.NORMAL
                );

            if (normal.found()) {
                normalFound++;
            }

            if (normal.uploaded()) {
                normalUploaded++;
            }

            if (normal.scaled()) {
                scaled++;
            }

            if (normal.failed()) {
                failed++;
            }

            UploadResult specular =
                uploadOne(
                    resources,
                    page,
                    sprite,
                    placement,
                    PagesOfAtlasPbrSourceResolver.Type.SPECULAR
                );

            if (specular.found()) {
                specularFound++;
            }

            if (specular.uploaded()) {
                specularUploaded++;
            }

            if (specular.scaled()) {
                scaled++;
            }

            if (specular.failed()) {
                failed++;
            }
        }

        PagesOfAtlasClient.LOGGER.info(
            "[PBR UPLOAD] page={} sprites={} normal={}/{} specular={}/{} scaled={} failed={}",
            page.page(),
            placements.size(),
            normalUploaded,
            normalFound,
            specularUploaded,
            specularFound,
            scaled,
            failed
        );
    }

    private static UploadResult uploadOne(
        ResourceManager resources,
        PagesOfAtlasPbrPage page,
        Identifier sprite,
        PagesOfAtlasRegistry.Placement placement,
        PagesOfAtlasPbrSourceResolver.Type type
    ) {
        var resourceOptional =
            PagesOfAtlasPbrSourceResolver.find(
                resources,
                sprite,
                type
            );

        if (resourceOptional.isEmpty()) {
            return UploadResult.NOT_FOUND;
        }

        Resource resource =
            resourceOptional.get();

        NativeImage image = null;
        NativeImage uploadImage = null;

        boolean scaled = false;

        try (
            InputStream stream =
                resource.open()
        ) {
            image =
                NativeImage.read(stream);

            /*
             * The diffuse atlas placement is authoritative.
             *
             * PBR images MUST occupy exactly the same logical
             * dimensions as the corresponding diffuse sprite.
             */
            var dimensionsOptional =
                PagesOfAtlasRegistry.spriteDimensions(
                    TextureAtlas.LOCATION_BLOCKS,
                    sprite
                );

            if (dimensionsOptional.isEmpty()) {
                PagesOfAtlasClient.LOGGER.error(
                    "[PBR UPLOAD] Missing POA dimensions for {}",
                    sprite
                );

                return UploadResult.FAILED;
            }

            var dimensions =
                dimensionsOptional.get();

            /*
             * EXPERIMENT:
             *
             * The diffuse placement remains authoritative, but the
             * physical PBR page is 1/N the diffuse resolution.
             *
             * Scaling the whole atlas and every sprite placement by
             * the same factor preserves normalized UV coordinates.
             */
            int divisor =
                PagesOfAtlasPbrPage.PBR_RESOLUTION_DIVISOR;

            int targetWidth =
                Math.max(
                    1,
                    dimensions.width() / divisor
                );

            int targetHeight =
                Math.max(
                    1,
                    dimensions.height() / divisor
                );

            if (
                image.getWidth() == targetWidth
                && image.getHeight() == targetHeight
            ) {
                uploadImage =
                    image;

            } else {
                /*
                 * PBR companions sometimes differ in resolution from
                 * the logical diffuse sprite. Scale them into POA's
                 * authoritative slot instead of allowing them to alter
                 * atlas layout.
                 *
                 * This also prevents a larger PBR image from writing
                 * into a neighboring POA slot.
                 */
                NativeImage scaledImage =
                    new NativeImage(
                        targetWidth,
                        targetHeight,
                        false
                    );

                image.resizeSubRectTo(
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight(),
                    scaledImage
                );

                uploadImage =
                    scaledImage;

                scaled = true;
            }
            /*
             * Bounds safety.
             */
            int logicalDestX =
                placement.x()
                    + placement.padding();

            int logicalDestY =
                placement.y()
                    + placement.padding();

            /*
             * The current POA packing grid is aligned well beyond
             * the 2x reduction used by this experiment.
             *
             * Refuse silently-corrupt placement if a future pack
             * produces coordinates that cannot be represented
             * exactly at this reduction factor.
             */
            if (
                logicalDestX % divisor != 0
                || logicalDestY % divisor != 0
                || dimensions.width() % divisor != 0
                || dimensions.height() % divisor != 0
            ) {
                PagesOfAtlasClient.LOGGER.error(
                    "[PBR UPLOAD] Cannot exactly downscale {} placement for {} by divisor {}: dest={},{} size={}x{}",
                    type,
                    sprite,
                    divisor,
                    logicalDestX,
                    logicalDestY,
                    dimensions.width(),
                    dimensions.height()
                );

                return UploadResult.FAILED;
            }

            int destX =
                logicalDestX / divisor;

            int destY =
                logicalDestY / divisor;

            if (
                destX + uploadImage.getWidth()
                    > page.width()
                ||
                destY + uploadImage.getHeight()
                    > page.height()
            ) {
                PagesOfAtlasClient.LOGGER.error(
                    "[PBR UPLOAD] {} {} does not fit page {} at {},{} size={}x{} page={}x{}",
                    type,
                    sprite,
                    page.page(),
                    destX,
                    destY,
                    uploadImage.getWidth(),
                    uploadImage.getHeight(),
                    page.width(),
                    page.height()
                );

                return UploadResult.FAILED;
            }

            /*
             * Submit while uploadImage is still alive.
             *
             * We intentionally use one encoder per source image for
             * this first correctness pass. Once page-1 PBR routing is
             * proven, this can be safely optimized into batches.
             */
            CommandEncoder encoder =
                RenderSystem.getDevice()
                    .createCommandEncoder();

            encoder.writeToTexture(
                type
                    == PagesOfAtlasPbrSourceResolver.Type.NORMAL
                        ? page.normalTexture()
                        : page.specularTexture(),
                uploadImage,
                0,
                0,
                destX,
                destY
            );

            encoder.submit();

            return new UploadResult(
                true,
                true,
                scaled,
                false
            );

        } catch (Throwable t) {
            PagesOfAtlasClient.LOGGER.error(
                "[PBR UPLOAD] Failed {} {}",
                type,
                sprite,
                t
            );

            return UploadResult.FAILED;

        } finally {
            if (
                uploadImage != null
                && uploadImage != image
            ) {
                uploadImage.close();
            }

            if (image != null) {
                image.close();
            }
        }
    }

    private record UploadResult(
        boolean found,
        boolean uploaded,
        boolean scaled,
        boolean failed
    ) {
        private static final UploadResult NOT_FOUND =
            new UploadResult(
                false,
                false,
                false,
                false
            );

        private static final UploadResult FAILED =
            new UploadResult(
                true,
                false,
                false,
                true
            );
    }
}
