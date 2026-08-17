package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasQuadTag;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Final PagesOfAtlas UV encoding point.
 *
 * Indigo performs all active QuadTransforms before invoking
 * emitDirectly().
 *
 * Therefore the final-page injection runs after Continuity has
 * completed its CTM manipulation but immediately before the quad
 * is written to the render buffer.
 */
@Mixin(
    targets =
        "net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl",
    remap = false
)
public abstract class IndigoMutableQuadViewMixin {

    /*
     * Vanilla BakedQuad -> Indigo MutableQuad.
     *
     * Start the quad with the physical page belonging to the
     * baked quad's sprite.
     *
     * fromBakedQuad() RETURNS MutableQuadViewImpl, so this
     * injection must use CallbackInfoReturnable.
     */
    @Inject(
        method = "fromBakedQuad",
        at = @At("RETURN"),
        remap = false
    )
    private void pagesofatlas$tagBakedQuad(
        BakedQuad bakedQuad,
        CallbackInfoReturnable<?> cir
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

    /*
     * transformAndEmit() is void, so ordinary CallbackInfo is
     * correct here.
     *
     * At this point all Continuity/Fabric quad transforms have
     * already run.
     */
    @Inject(
        method = "transformAndEmit",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;emitDirectly()V",
            shift = At.Shift.BEFORE
        ),
        remap = false
    )
    private void pagesofatlas$encodeFinalPage(
        CallbackInfo ci
    ) {
        MutableQuadView quad =
            (MutableQuadView)(Object)this;

        int tag =
            quad.tag();

        if (!PagesOfAtlasQuadTag.isPagesOfAtlasTag(tag)) {
            return;
        }

        int page =
            PagesOfAtlasQuadTag.page(tag);

        if (page <= 0) {
            return;
        }

        float offset =
            page * 2.0F;

        for (
            int vertex = 0;
            vertex < 4;
            vertex++
        ) {
            quad.uv(
                vertex,
                quad.u(vertex) + offset,
                quad.v(vertex)
            );
        }
    }
}
