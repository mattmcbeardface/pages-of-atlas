package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasQuadTag;


import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Continuity compatibility.
 *
 * Continuity's CTM processor can replace the sprite represented
 * by an existing quad.
 *
 * PagesOfAtlas therefore updates the quad's physical-page tag after
 * Continuity has completed its UV interpolation.
 *
 * UVs remain completely normal here. The physical-page offset is
 * added later by IndigoMutableQuadViewMixin immediately before the
 * quad is emitted.
 */
@Mixin(
    targets = "me.pepperbell.continuity.client.util.QuadUtil",
    remap = false
)
public abstract class ContinuityQuadUtilMixin {

    @Inject(
        method =
            "interpolate(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableQuadView;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void pagesofatlas$updateInterpolatedPage(
        MutableQuadView quad,
        TextureAtlasSprite oldSprite,
        TextureAtlasSprite newSprite,
        CallbackInfo ci
    ) {
        int newTag =
            PagesOfAtlasQuadTag.encodeSprite(
                newSprite
            );

        quad.tag(
            newTag
        );

    }
}
