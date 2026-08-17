package com.pagesofatlas.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.pagesofatlas.api.PagedSprite;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/*
 * PagesOfAtlas block-item compatibility.
 *
 * Minecraft 26.2 renders ordinary inventory/hand item quads
 * through ItemFeatureRenderer, NOT BlockModelFeatureRenderer.
 *
 * PagesOfAtlas item shaders select the physical atlas page from
 * an encoded U range:
 *
 * page 0 -> normal U
 * page 1 -> U + 2
 * page 2 -> U + 4
 * page 3 -> U + 6
 *
 * Terrain/Sodium must never use this encoding because Sodium
 * compresses terrain UVs separately.
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

    @Redirect(
        method = "prepareMainSubmit",
        at = @At(
            value = "INVOKE",
            target =
                "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(" +
                "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;" +
                "Lnet/minecraft/client/resources/model/geometry/BakedQuad;" +
                "Lcom/mojang/blaze3d/vertex/QuadInstance;)V"
        )
    )
    private void pagesofatlas$encodeMainItemPage(
        VertexConsumer original,
        PoseStack.Pose pose,
        BakedQuad quad,
        QuadInstance instance
    ) {
        pagesofatlas$putPagedQuad(
            original,
            pose,
            quad,
            instance
        );
    }

    private static void pagesofatlas$putPagedQuad(
        VertexConsumer original,
        PoseStack.Pose pose,
        BakedQuad quad,
        QuadInstance instance
    ) {
        TextureAtlasSprite sprite =
            quad.materialInfo().sprite();

        int page = 0;

        if (sprite instanceof PagedSprite paged) {
            page =
                paged.pagesofatlas$getPage();
        }

        /*
         * Page zero needs no special treatment.
         */
        if (page <= 0) {
            original.putBakedQuad(
                pose,
                quad,
                instance
            );

            return;
        }

        final float uOffset =
            page * 2.0F;

        /*
         * Wrap the actual item VertexConsumer and modify only
         * UV0. Everything else passes straight through.
         */
        VertexConsumer wrapped =
            new VertexConsumer() {

                @Override
                public VertexConsumer addVertex(
                    float x,
                    float y,
                    float z
                ) {
                    original.addVertex(
                        x,
                        y,
                        z
                    );

                    return this;
                }

                @Override
                public VertexConsumer setColor(
                    int r,
                    int g,
                    int b,
                    int a
                ) {
                    original.setColor(
                        r,
                        g,
                        b,
                        a
                    );

                    return this;
                }

                @Override
                public VertexConsumer setColor(
                    int color
                ) {
                    original.setColor(
                        color
                    );

                    return this;
                }

                @Override
                public VertexConsumer setUv(
                    float u,
                    float v
                ) {
                    original.setUv(
                        u + uOffset,
                        v
                    );

                    return this;
                }

                @Override
                public VertexConsumer setUv1(
                    int u,
                    int v
                ) {
                    original.setUv1(
                        u,
                        v
                    );

                    return this;
                }

                @Override
                public VertexConsumer setUv2(
                    int u,
                    int v
                ) {
                    original.setUv2(
                        u,
                        v
                    );

                    return this;
                }

                @Override
                public VertexConsumer setNormal(
                    float x,
                    float y,
                    float z
                ) {
                    original.setNormal(
                        x,
                        y,
                        z
                    );

                    return this;
                }

                @Override
                public VertexConsumer setLineWidth(
                    float width
                ) {
                    original.setLineWidth(
                        width
                    );

                    return this;
                }
            };

        wrapped.putBakedQuad(
            pose,
            quad,
            instance
        );
    }
}
