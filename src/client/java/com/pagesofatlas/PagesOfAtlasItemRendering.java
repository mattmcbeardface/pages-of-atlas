package com.pagesofatlas;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.pagesofatlas.compat.SodiumQuadUvAccess;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
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

    private static final PageLayout EMPTY_LAYOUT =
        new PageLayout(
            -1L,
            new int[0],
            new int[0]
        );

    private static volatile PageLayout currentLayout =
        EMPTY_LAYOUT;

    private static volatile boolean reportedInvalidVirtualQuad;

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
            pageLayout().pageCount();

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

    public static int virtualPage(
        BakedQuad quad
    ) {
        long uv0 = quad.packedUV0();
        long uv1 = quad.packedUV1();
        long uv2 = quad.packedUV2();
        long uv3 = quad.packedUV3();

        return virtualPage(
            UVPair.unpackU(uv0),
            UVPair.unpackV(uv0),
            UVPair.unpackU(uv1),
            UVPair.unpackV(uv1),
            UVPair.unpackU(uv2),
            UVPair.unpackV(uv2),
            UVPair.unpackU(uv3),
            UVPair.unpackV(uv3)
        );
    }

    public static int virtualPage(
        QuadView quad
    ) {
        return virtualPage(
            quad.u(0),
            quad.v(0),
            quad.u(1),
            quad.v(1),
            quad.u(2),
            quad.v(2),
            quad.u(3),
            quad.v(3)
        );
    }

    public static int virtualPage(
        SodiumQuadUvAccess quad
    ) {
        return virtualPage(
            quad.pagesofatlas$getTexU(0),
            quad.pagesofatlas$getTexV(0),
            quad.pagesofatlas$getTexU(1),
            quad.pagesofatlas$getTexV(1),
            quad.pagesofatlas$getTexU(2),
            quad.pagesofatlas$getTexV(2),
            quad.pagesofatlas$getTexU(3),
            quad.pagesofatlas$getTexV(3)
        );
    }

    private static int virtualPage(
        float u0,
        float v0,
        float u1,
        float v1,
        float u2,
        float v2,
        float u3,
        float v3
    ) {
        int page0 = virtualPage(u0, v0);
        int page1 = virtualPage(u1, v1);
        int page2 = virtualPage(u2, v2);
        int page3 = virtualPage(u3, v3);

        if (
            page0 >= 0
            && page1 == page0
            && page2 == page0
            && page3 == page0
        ) {
            return page0;
        }

        if (!reportedInvalidVirtualQuad) {
            reportedInvalidVirtualQuad = true;

            PagesOfAtlasClient.LOGGER.error(
                "[VIRTUAL ATLAS] Item quad did not resolve to one physical page; pages={},{},{},{} uv0=({}, {}) uv1=({}, {}) uv2=({}, {}) uv3=({}, {})",
                page0,
                page1,
                page2,
                page3,
                u0,
                v0,
                u1,
                v1,
                u2,
                v2,
                u3,
                v3
            );
        }

        return -1;
    }

    private static int virtualPage(
        float u,
        float v
    ) {
        if (!Float.isFinite(u) || !Float.isFinite(v)) {
            return -1;
        }

        int pageX =
            (int)Math.floor(
                u
                    * PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE
                    / PagesOfAtlasVirtualAtlas.CELL_SIZE
            );

        int pageY =
            (int)Math.floor(
                v
                    * PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE
                    / PagesOfAtlasVirtualAtlas.CELL_SIZE
            );

        if (
            pageX < 0
            || pageX >= 2
            || pageY < 0
            || pageY >= 2
        ) {
            return -1;
        }

        return pageY * 2 + pageX;
    }

    private static PageLayout pageLayout() {
        PagesOfAtlasRegistry.AtlasPlan plan =
            PagesOfAtlasRegistry.activePlan(
                TextureAtlas.LOCATION_BLOCKS
            );

        if (plan == null) {
            return EMPTY_LAYOUT;
        }

        PageLayout layout = currentLayout;

        if (layout.generation() == plan.generation()) {
            return layout;
        }

        synchronized (PagesOfAtlasItemRendering.class) {
            layout = currentLayout;

            if (layout.generation() == plan.generation()) {
                return layout;
            }

            int pageCount = plan.pageCount();
            int[] widths = new int[pageCount];
            int[] heights = new int[pageCount];

            for (
                PagesOfAtlasRegistry.PagePlan page :
                plan.pages()
            ) {
                int pageNumber = page.page();

                if (
                    pageNumber >= 0
                    && pageNumber < pageCount
                ) {
                    widths[pageNumber] = page.width();
                    heights[pageNumber] = page.height();
                }
            }

            layout =
                new PageLayout(
                    plan.generation(),
                    widths,
                    heights
                );

            currentLayout = layout;
            return layout;
        }
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
            public VertexConsumer setUv3(
                float u,
                float v
            ) {
                original.setUv3(u, v);
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

    public static final class VirtualPageLocalConsumer
        implements VertexConsumer {

        private VertexConsumer delegate;
        private float uScale;
        private float vScale;
        private float uOffset;
        private float vOffset;

        public VertexConsumer wrap(
            VertexConsumer original,
            int page
        ) {
            PageLayout layout = pageLayout();

            if (
                page < 0
                || page >= layout.pageCount()
            ) {
                return original;
            }

            int width = layout.widths()[page];
            int height = layout.heights()[page];

            if (width <= 0 || height <= 0) {
                return original;
            }

            delegate = original;
            uScale =
                (float)PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE
                    / width;
            vScale =
                (float)PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE
                    / height;
            uOffset =
                (float)(
                    PagesOfAtlasVirtualAtlas.cellX(page)
                    * PagesOfAtlasVirtualAtlas.CELL_SIZE
                ) / width;
            vOffset =
                (float)(
                    PagesOfAtlasVirtualAtlas.cellY(page)
                    * PagesOfAtlasVirtualAtlas.CELL_SIZE
                ) / height;

            return this;
        }

        @Override
        public VertexConsumer addVertex(
            float x,
            float y,
            float z
        ) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(
            int r,
            int g,
            int b,
            int a
        ) {
            delegate.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(
            float u,
            float v
        ) {
            delegate.setUv(
                u * uScale - uOffset,
                v * vScale - vOffset
            );
            return this;
        }

        @Override
        public VertexConsumer setUv1(
            int u,
            int v
        ) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(
            int u,
            int v
        ) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv3(
            float u,
            float v
        ) {
            delegate.setUv3(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(
            float x,
            float y,
            float z
        ) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
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
                .setOitPipelines(
                    RenderPipelines.OIT_ITEM
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

    private record PageLayout(
        long generation,
        int[] widths,
        int[] heights
    ) {
        private int pageCount() {
            return widths.length;
        }
    }
}
