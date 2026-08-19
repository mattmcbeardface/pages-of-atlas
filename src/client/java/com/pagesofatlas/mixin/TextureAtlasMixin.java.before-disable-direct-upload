package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasClient;
import com.pagesofatlas.PagesOfAtlasDirectUploader;
import com.pagesofatlas.PagesOfAtlasRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

    @Shadow
    @Final
    private Identifier location;

    @Shadow
    private Map<Identifier, TextureAtlasSprite> texturesByName;

    @Shadow
    private int maxMipLevel;

    /*
     * Vanilla calls this at the end of uploadInitialContents().
     * Because POA page 1 cancels that method, we must make the call
     * ourselves after directly uploading the static sprites.
     */
    @Shadow
    private void uploadAnimationFrames() {
        throw new AssertionError();
    }

    @Unique
    private static final ThreadLocal<Boolean>
        pagesofatlas$internalUpload =
            ThreadLocal.withInitial(() -> false);

    /*
     * EXPERIMENT:
     *
     * Page 1 only bypasses vanilla TextureAtlas.uploadInitialContents().
     *
     * Vanilla creates temporary GPU textures for individual sprites and
     * then blits them into the final atlas. With very large high-resolution
     * resource packs that path can create a substantial transient-memory
     * spike.
     *
     * Pages 0, 2 and 3 continue using vanilla behavior.
     */
    @Inject(
        method = "uploadInitialContents",
        at = @At("HEAD"),
        cancellable = true
    )
    private void pagesofatlas$directUploadPageOne(
        CallbackInfo ci
    ) {
        Identifier pageOne =
            PagesOfAtlasRegistry.physicalAtlasLocation(
                TextureAtlas.LOCATION_BLOCKS,
                1
            );

        if (!this.location.equals(pageOne)) {
            return;
        }

        try {
            PagesOfAtlasClient.LOGGER.info(
                "[DIRECT UPLOAD] Bypassing vanilla initial upload for {}",
                this.location
            );

            AbstractTexture texture =
                (AbstractTexture)(Object)this;

            PagesOfAtlasDirectUploader.uploadBlockPage(
                texture.getTexture(),
                this.texturesByName.values(),
                this.maxMipLevel
            );

            /*
             * Preserve the final step of vanilla
             * TextureAtlas.uploadInitialContents().
             *
             * This uploads the current frames for animated sprites
             * using Minecraft's already-created animation states.
             */
            this.uploadAnimationFrames();

            ci.cancel();

        } catch (Throwable t) {
            PagesOfAtlasClient.LOGGER.error(
                "[DIRECT UPLOAD] Page 1 direct upload failed; falling back to vanilla",
                t
            );

            /*
             * Do not cancel. Vanilla uploadInitialContents()
             * will execute if our experimental path fails.
             */
        }
    }

    @Inject(
        method = "upload",
        at = @At("HEAD"),
        cancellable = true
    )
    private void pagesofatlas$uploadPages(
        SpriteLoader.Preparations preparations,
        CallbackInfo ci
    ) {
        /*
         * Recursive page uploads must be allowed to execute
         * Minecraft's normal TextureAtlas.upload().
         */
        if (pagesofatlas$internalUpload.get()) {
            return;
        }

        var bundleOptional =
            PagesOfAtlasRegistry.uploadBundle(
                location
            );

        /*
         * Normal vanilla atlas.
         */
        if (bundleOptional.isEmpty()) {
            return;
        }

        PagesOfAtlasRegistry.UploadBundle bundle =
            bundleOptional.get();

        pagesofatlas$internalUpload.set(true);

        try {
            TextureManager textureManager =
                Minecraft.getInstance()
                    .getTextureManager();

            for (
                PagesOfAtlasRegistry.PageUpload page :
                bundle.pages()
            ) {
                TextureAtlas targetAtlas;

                /*
                 * Page 0 uses Minecraft's original block atlas.
                 */
                if (page.page() == 0) {
                    targetAtlas =
                        (TextureAtlas)(Object)this;
                } else {
                    AbstractTexture existing =
                        textureManager.getTexture(
                            page.physicalAtlas()
                        );

                    if (existing instanceof TextureAtlas atlas) {
                        targetAtlas = atlas;
                    } else {
                        /*
                         * Fallback in case registration didn't
                         * happen earlier for some reason.
                         */
                        targetAtlas =
                            new TextureAtlas(
                                page.physicalAtlas()
                            );

                        textureManager.register(
                            page.physicalAtlas(),
                            targetAtlas
                        );

                    }
                }

                /*
                 * Re-enters TextureAtlas.upload().
                 *
                 * pagesofatlas$internalUpload=true causes the
                 * Mixin to stand aside and let vanilla perform
                 * the actual GPU texture creation/upload.
                 */
                targetAtlas.upload(
                    page.preparations()
                );

            }

            /*
             * We already uploaded the logical atlas as its
             * individual physical pages. Do NOT let vanilla
             * upload the combined preparation afterward.
             */
            ci.cancel();

        } catch (Throwable t) {
            PagesOfAtlasClient.LOGGER.error(
                "PagesOfAtlas physical upload failed for {}",
                location,
                t
            );

            /*
             * Do not cancel here. If our upload fails,
             * Minecraft gets a chance to execute its normal
             * upload path rather than immediately crashing.
             */

        } finally {
            pagesofatlas$internalUpload.set(false);
        }
    }
}
