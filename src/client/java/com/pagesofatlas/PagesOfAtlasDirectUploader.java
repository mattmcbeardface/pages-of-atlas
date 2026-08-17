package com.pagesofatlas;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import com.pagesofatlas.mixin.SpriteContentsAccessor;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class PagesOfAtlasDirectUploader {

    private PagesOfAtlasDirectUploader() {}

    public static void uploadBlockPage(
        GpuTexture atlasTexture,
        Collection<TextureAtlasSprite> sprites,
        int maxMipLevel
    ) {
        CommandEncoder encoder =
            RenderSystem.getDevice()
                .createCommandEncoder();

        /*
         * A secondary POA atlas may alias one sprite as missingno.
         * Do not upload the same sprite object twice.
         */
        Set<TextureAtlasSprite> visited =
            Collections.newSetFromMap(
                new IdentityHashMap<>()
            );

        int uploadedSprites = 0;
        int uploadedImages = 0;

        for (TextureAtlasSprite sprite : sprites) {

            if (!visited.add(sprite)) {
                continue;
            }

            /*
             * Animated sprites are handled by TextureAtlas's normal
             * animation machinery. Their NativeImage may represent an
             * entire animation sheet rather than one atlas frame.
             */
            if (sprite.isAnimated()) {
                continue;
            }

            var placementOptional =
                PagesOfAtlasRegistry.lookup(
                    TextureAtlas.LOCATION_BLOCKS,
                    sprite.contents().name()
                );

            if (placementOptional.isEmpty()) {
                PagesOfAtlasClient.LOGGER.warn(
                    "[DIRECT UPLOAD] No POA placement for {}",
                    sprite.contents().name()
                );

                continue;
            }

            var placement =
                placementOptional.get();

            NativeImage[] mipImages =
                ((SpriteContentsAccessor)(Object)sprite.contents())
                    .pagesofatlas$getByMipLevel();

            int highestMip =
                Math.min(
                    maxMipLevel,
                    mipImages.length - 1
                );

            for (int mip = 0; mip <= highestMip; mip++) {

                NativeImage image =
                    mipImages[mip];

                if (image == null) {
                    continue;
                }

                /*
                 * Placement x/y describes the padded atlas slot.
                 * The actual image begins inside that slot after
                 * the padding margin.
                 */
                int destX =
                    (placement.x()
                        + placement.padding())
                    >> mip;

                int destY =
                    (placement.y()
                        + placement.padding())
                    >> mip;

                encoder.writeToTexture(
                    atlasTexture,
                    image,
                    mip,
                    0,
                    destX,
                    destY
                );

                uploadedImages++;
            }

            uploadedSprites++;
        }

        encoder.submit();

        PagesOfAtlasClient.LOGGER.info(
            "[DIRECT UPLOAD] Uploaded {} sprites / {} mip images directly into POA page",
            uploadedSprites,
            uploadedImages
        );
    }
}
