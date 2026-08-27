package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasParticleLayers;
import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Preserve a block particle sprite's physical PoA page in its render layer.
 */
@Mixin(SingleQuadParticle.Layer.class)
public abstract class SingleQuadParticleLayerMixin {

    @Inject(
        method = "bySprite",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void pagesofatlas$physicalBlockAtlasLayer(
        TextureAtlasSprite sprite,
        CallbackInfoReturnable<SingleQuadParticle.Layer> cir
    ) {
        if (
            !sprite.atlasLocation()
                .equals(TextureAtlas.LOCATION_BLOCKS)
            || !(sprite instanceof PagedSprite paged)
        ) {
            return;
        }

        int page =
            paged.pagesofatlas$getPage();

        if (page <= 0) {
            return;
        }

        cir.setReturnValue(
            PagesOfAtlasParticleLayers.pageAware(
                cir.getReturnValue(),
                page
            )
        );
    }
}
