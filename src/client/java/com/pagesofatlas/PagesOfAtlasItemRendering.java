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
 * Each physical atlas page gets a distinct RenderType, while all of those
 * RenderTypes retain the two PoA item RenderPipeline identities registered
 * with Iris. The selected physical page is bound as the ordinary Sampler0,
 * so both Minecraft's item shader and an Iris shader-pack program receive
 * normal page-local UVs and a normal diffuse texture.
 */
public final class PagesOfAtlasItemRendering {

    private PagesOfAtlasItemRendering() {}

    private static final ConcurrentMap<Key, RenderType> TYPES =
        new ConcurrentHashMap<>();

    public static RenderType pageAware(
        RenderType original,
        int page
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

        if (
            pageCount <= 1
            || page < 0
            || page >= pageCount
        ) {
            return original;
        }

        Key key =
            new Key(
                page,
                translucent
            );

        return TYPES.computeIfAbsent(
            key,
            PagesOfAtlasItemRendering::create
        );
    }

    /**
     * Indigo has already added its page stride before it dispatches an item
     * quad. Remove only that item-path encoding because page selection now
     * lives in the RenderType's Sampler0 binding.
     */
    public static VertexConsumer pageLocal(
        VertexConsumer original,
        int page
    ) {
        if (page <= 0) {
            return original;
        }

        float uOffset =
            page * PagedTextureAtlasSprite.PAGE_U_STRIDE;

        return new VertexConsumer() {

            @Override
            public VertexConsumer addVertex(
                float x,
                float y,
                float z
            ) {
                original.addVertex(x, y, z);
                return this;
            }

            @Override
            public VertexConsumer setColor(
                int r,
                int g,
                int b,
                int a
            ) {
                original.setColor(r, g, b, a);
                return this;
            }

            @Override
            public VertexConsumer setColor(
                int color
            ) {
                original.setColor(color);
                return this;
            }

            @Override
            public VertexConsumer setUv(
                float u,
                float v
            ) {
                original.setUv(
                    u - uOffset,
                    v
                );
                return this;
            }

            @Override
            public VertexConsumer setUv1(
                int u,
                int v
            ) {
                original.setUv1(u, v);
                return this;
            }

            @Override
            public VertexConsumer setUv2(
                int u,
                int v
            ) {
                original.setUv2(u, v);
                return this;
            }

            @Override
            public VertexConsumer setNormal(
                float x,
                float y,
                float z
            ) {
                original.setNormal(x, y, z);
                return this;
            }

            @Override
            public VertexConsumer setLineWidth(
                float width
            ) {
                original.setLineWidth(width);
                return this;
            }
        };
    }

    private static RenderType create(
        Key key
    ) {
        var pipeline =
            key.translucent()
                ? PagesOfAtlasRenderPipelines.ITEM_TRANSLUCENT
                : PagesOfAtlasRenderPipelines.ITEM_CUTOUT;

        Identifier texture =
            pageTexture(key.page());

        RenderSetup.RenderSetupBuilder setup =
            RenderSetup.builder(
                pipeline
            )
                .withTexture(
                    "Sampler0",
                    texture
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

        return RenderType.create(
            "pagesofatlas_"
                + (key.translucent()
                    ? "translucent"
                    : "cutout")
                + "_block_item_page_"
                + key.page(),
            setup.createRenderSetup()
        );
    }

    private static Identifier pageTexture(
        int page
    ) {
        if (page == 0) {
            return TextureAtlas.LOCATION_BLOCKS;
        }

        return PagesOfAtlasRegistry.physicalAtlasLocation(
            TextureAtlas.LOCATION_BLOCKS,
            page
        );
    }

    private record Key(
        int page,
        boolean translucent
    ) {}
}
