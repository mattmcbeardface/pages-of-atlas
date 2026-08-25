package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasClient;
import com.pagesofatlas.PagesOfAtlasRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Routes Minecraft's existing single-submit painting renderer to the
 * physical painting atlas that owns the current front sprite.
 */
@Mixin(PaintingRenderer.class)
public abstract class PaintingRendererMixin {

    @Unique
    private static final ThreadLocal<Identifier>
        pagesofatlas$frontPhysicalAtlas =
            new ThreadLocal<>();

    @Redirect(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/texture/TextureAtlas;getSprite(" +
                "Lnet/minecraft/resources/Identifier;)" +
                "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
            ordinal = 0
        )
    )
    private TextureAtlasSprite pagesofatlas$lookupFront(
        TextureAtlas logicalAtlas,
        Identifier spriteId
    ) {
        TextureAtlasSprite front =
            logicalAtlas.getSprite(spriteId);

        /*
         * Paged painting sprites carry their physical atlas identifier.
         * Vanilla/non-paged sprites carry the normal logical identifier,
         * making the back lookup below a no-op in that case.
         */
        pagesofatlas$frontPhysicalAtlas.set(
            front.atlasLocation()
        );

        return front;
    }

    @Redirect(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/texture/TextureAtlas;getSprite(" +
                "Lnet/minecraft/resources/Identifier;)" +
                "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
            ordinal = 1
        )
    )
    private TextureAtlasSprite pagesofatlas$lookupBackOnFrontPage(
        TextureAtlas logicalAtlas,
        Identifier backSpriteId
    ) {
        Identifier physicalAtlas =
            pagesofatlas$frontPhysicalAtlas.get();

        pagesofatlas$frontPhysicalAtlas.remove();

        if (physicalAtlas == null ||
            physicalAtlas.equals(logicalAtlas.location())) {

            return logicalAtlas.getSprite(
                backSpriteId
            );
        }

        /*
         * Only painting pages are supported by this adapter. A foreign
         * atlas identifier falls back to vanilla rather than broadening
         * this into generic non-block-atlas routing.
         */
        if (PagesOfAtlasRegistry
                .plan(Sheets.PAINTINGS_SHEET)
                .flatMap(plan ->
                    plan.pages()
                        .stream()
                        .filter(page ->
                            page.physicalAtlas()
                                .equals(physicalAtlas)
                        )
                        .findFirst()
                )
                .isEmpty()) {

            return logicalAtlas.getSprite(
                backSpriteId
            );
        }

        AbstractTexture physicalTexture =
            Minecraft.getInstance()
                .getTextureManager()
                .getTexture(physicalAtlas);

        if (physicalTexture instanceof TextureAtlas pageAtlas) {
            return pageAtlas.getSprite(
                backSpriteId
            );
        }

        PagesOfAtlasClient.LOGGER.warn(
            "Painting sprite requested physical atlas {}, but it was not a TextureAtlas",
            physicalAtlas
        );

        return logicalAtlas.getSprite(
            backSpriteId
        );
    }
}
