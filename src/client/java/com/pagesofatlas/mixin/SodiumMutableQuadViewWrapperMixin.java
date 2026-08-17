package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasQuadTag;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Sodium / FRAPI compatibility.
 *
 * Sodium compresses terrain UV coordinates to 15-bit values,
 * so PagesOfAtlas MUST NOT encode the atlas page into U.
 *
 * Instead we preserve ordinary UV coordinates and carry the
 * PagesOfAtlas physical-page number through Fabric's quad tag.
 *
 * ContinuityQuadUtilMixin may later replace this tag when CTM
 * changes the quad's sprite.
 */
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.frapi.wrapper.MutableQuadViewWrapper",
    remap = false
)
public abstract class SodiumMutableQuadViewWrapperMixin {

    @Inject(
        method = "fromBakedQuad",
        at = @At("RETURN"),
        remap = false
    )
    private void pagesofatlas$tagBakedQuad(
        BakedQuad bakedQuad,
        CallbackInfoReturnable<QuadEmitter> cir
    ) {
        MutableQuadView quad =
            (MutableQuadView)(Object)this;

        TextureAtlasSprite sprite =
            bakedQuad.materialInfo().sprite();

        quad.tag(
            PagesOfAtlasQuadTag.encodeSprite(
                sprite
            )
        );
    }
}
