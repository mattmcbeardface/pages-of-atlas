package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasClient;
import com.pagesofatlas.PagesOfAtlasDirectUploader;
import com.pagesofatlas.PagesOfAtlasPhysicalAtlases;
import com.pagesofatlas.PagesOfAtlasRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
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
    private TextureAtlasSprite missingSprite;

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
    /*
     * Physical POA pages currently use Minecraft's normal
     * uploadInitialContents() path.
     *
     * The experimental direct uploader remains disabled until its
     * memory benefits can be validated independently from shader
     * compatibility.
     */
    @Inject(
        method = "uploadInitialContents",
        at = @At("HEAD"),
        cancellable = true
    )
    private void pagesofatlas$directUploadPageOne(
        CallbackInfo ci
    ) {
        // Intentionally allow vanilla uploadInitialContents().
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

        if (
            preparations != bundle.combined()
            ||
            !PagesOfAtlasRegistry.isStagedGeneration(
                location,
                bundle.generation()
            )
        ) {
            ci.cancel();
            return;
        }

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

            pagesofatlas$validatePhysicalPages(
                textureManager,
                bundle
            );

            if (location.equals(Sheets.PAINTINGS_SHEET)) {
                /*
                 * TextureAtlas.upload(pageZero) installs page zero's
                 * physical lookup map on the logical painting atlas.
                 * PaintingRenderer, however, performs direct atlas
                 * lookups rather than using AtlasManager's combined map.
                 *
                 * Restore the combined map as a non-owning lookup view.
                 * We intentionally do not change TextureAtlas.sprites:
                 * that private list remains page zero only, so closing
                 * the logical atlas cannot close sprites owned by a
                 * secondary physical atlas.
                 */
                texturesByName =
                    Map.copyOf(
                        bundle.combined()
                            .regions()
                    );

                missingSprite =
                    bundle.combined()
                        .missing();
            }

            if (
                PagesOfAtlasRegistry.activatePaged(
                    location,
                    bundle
                )
            ) {
                PagesOfAtlasPhysicalAtlases.completePagedAtlas(
                    location,
                    bundle
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

    @Inject(
        method = "upload",
        at = @At("RETURN")
    )
    private void pagesofatlas$completeVanillaUpload(
        SpriteLoader.Preparations preparations,
        CallbackInfo ci
    ) {
        if (
            pagesofatlas$internalUpload.get()
            || PagesOfAtlasRegistry
                .uploadBundle(location)
                .isPresent()
        ) {
            return;
        }

        long generation =
            PagesOfAtlasRegistry.activateVanilla(
                location,
                preparations
            );

        if (generation >= 0) {
            PagesOfAtlasPhysicalAtlases.completeVanillaAtlas(
                location,
                generation
            );
        }
    }

    @Unique
    private static void pagesofatlas$validatePhysicalPages(
        TextureManager textureManager,
        PagesOfAtlasRegistry.UploadBundle bundle
    ) {
        for (
            PagesOfAtlasRegistry.PageUpload page :
            bundle.pages()
        ) {
            if (page.page() <= 0) {
                continue;
            }

            AbstractTexture texture =
                textureManager.getTexture(
                    page.physicalAtlas()
                );

            if (!(texture instanceof TextureAtlas)) {
                throw new IllegalStateException(
                    "PagesOfAtlas physical page did not remain a TextureAtlas: "
                        + page.physicalAtlas()
                );
            }

            /*
             * getTextureView() throws if upload() has not initialized
             * the GPU texture. Reaching activation therefore proves
             * every secondary page in this generation is renderable.
             */
            texture.getTextureView();
        }
    }
}
