package com.pagesofatlas;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * Page-aware replacements for Minecraft's two block-item render types.
 *
 * The baked quad still decides whether the draw is cutout or translucent.
 * We replace only the two vanilla block-atlas item types and leave item-atlas
 * and mod-defined render types untouched.
 */
public final class PagesOfAtlasItemRendering {

    private PagesOfAtlasItemRendering() {}

    private static final ConcurrentMap<Key, RenderType> TYPES =
        new ConcurrentHashMap<>();

    public static RenderType pageAware(
        RenderType original
    ) {
        boolean translucent;

        if (original == Sheets.cutoutBlockItemSheet()) {
            translucent = false;
        } else if (
            original == Sheets.translucentBlockItemSheet()
        ) {
            translucent = true;
        } else {
            return original;
        }

        int pageCount =
            PagesOfAtlasRegistry
                .plan(TextureAtlas.LOCATION_BLOCKS)
                .map(
                    PagesOfAtlasRegistry.AtlasPlan::pageCount
                )
                .orElse(1);

        if (pageCount <= 1) {
            return original;
        }

        Key key =
            new Key(
                pageCount,
                translucent
            );

        return TYPES.computeIfAbsent(
            key,
            PagesOfAtlasItemRendering::create
        );
    }

    public static VertexConsumer pageAware(
        VertexConsumer original,
        int page
    ) {
        if (page <= 0) {
            return original;
        }

        float uOffset =
            page * 2.0F;

        return new VertexConsumer() {

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
    }

    private static RenderType create(
        Key key
    ) {
        RenderSetup.RenderSetupBuilder setup =
            RenderSetup.builder(
                key.translucent()
                    ? PagesOfAtlasRenderPipelines.ITEM_TRANSLUCENT
                    : PagesOfAtlasRenderPipelines.ITEM_CUTOUT
            )
                .withTexture(
                    "Sampler0",
                    TextureAtlas.LOCATION_BLOCKS
                )
                .withTexture(
                    "Sampler3",
                    pageTexture(key.pageCount(), 1)
                )
                .withTexture(
                    "Sampler4",
                    pageTexture(key.pageCount(), 2)
                )
                .withTexture(
                    "Sampler5",
                    pageTexture(key.pageCount(), 3)
                )
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(
                    RenderSetup.OutlineProperty.AFFECTS_OUTLINE
                );

        if (key.translucent()) {
            setup
                .setOutputTarget(
                    OutputTarget.ITEM_ENTITY_TARGET
                )
                .sortOnUpload();
        }

        PagesOfAtlasClient.LOGGER.debug(
            "Created {} page-aware block-item render type for {} atlas pages",
            key.translucent()
                ? "translucent"
                : "cutout",
            key.pageCount()
        );

        return RenderType.create(
            "pagesofatlas_"
                + (key.translucent()
                    ? "translucent"
                    : "cutout")
                + "_block_item_"
                + key.pageCount()
                + "_pages",
            setup.createRenderSetup()
        );
    }

    private static Identifier pageTexture(
        int pageCount,
        int page
    ) {
        if (page >= pageCount) {
            return TextureAtlas.LOCATION_BLOCKS;
        }

        return PagesOfAtlasRegistry.physicalAtlasLocation(
            TextureAtlas.LOCATION_BLOCKS,
            page
        );
    }

    private record Key(
        int pageCount,
        boolean translucent
    ) {}
}
