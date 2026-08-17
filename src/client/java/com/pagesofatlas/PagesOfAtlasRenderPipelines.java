package com.pagesofatlas;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class PagesOfAtlasRenderPipelines {

    private PagesOfAtlasRenderPipelines() {}

    /*
     * Maximum physical block-atlas pages supported by
     * the current fixed shader sampler layout.
     */
    public static final int MAX_BLOCK_PAGES = 4;


    /*
     * ============================================================
     * TERRAIN
     * ============================================================
     *
     * Sampler0 = block atlas page 0
     * Sampler1 = block atlas page 1
     * Sampler2 = vanilla lightmap
     * Sampler3 = block atlas page 2
     * Sampler4 = block atlas page 3
     */

    public static final BindGroupLayout TERRAIN_SPLIT_SAMPLERS =
        BindGroupLayout.builder()
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withSampler("Sampler3")
            .withSampler("Sampler4")
            .build();

    private static final Identifier TERRAIN_VERTEX_SHADER =
        Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            "core/terrain_split"
        );

    private static final Identifier TERRAIN_FRAGMENT_SHADER =
        Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            "core/terrain_split"
        );

    private static final RenderPipeline.Snippet TERRAIN =
        RenderPipeline.builder(
            RenderPipelines.GLOBALS_SNIPPET
        )
            .withBindGroupLayout(
                BindGroupLayouts.FOG
            )
            .withBindGroupLayout(
                TERRAIN_SPLIT_SAMPLERS
            )
            .withVertexBinding(
                0,
                DefaultVertexFormat.BLOCK
            )
            .withPrimitiveTopology(
                PrimitiveTopology.QUADS
            )
            .withDepthStencilState(
                DepthStencilState.DEFAULT
            )
            .withBindGroupLayout(
                BindGroupLayouts.PROJECTION
            )
            .withBindGroupLayout(
                BindGroupLayouts.CHUNK_SECTION
            )
            .withVertexShader(
                TERRAIN_VERTEX_SHADER
            )
            .withFragmentShader(
                TERRAIN_FRAGMENT_SHADER
            )
            .buildSnippet();

    public static final RenderPipeline SOLID =
        RenderPipelines.register(
            RenderPipeline.builder(TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/solid_terrain"
                    )
                )
                .build()
        );

    public static final RenderPipeline CUTOUT =
        RenderPipelines.register(
            RenderPipeline.builder(TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/cutout_terrain"
                    )
                )
                .withShaderDefine(
                    "ALPHA_CUTOUT",
                    0.5F
                )
                .build()
        );

    public static final RenderPipeline TRANSLUCENT =
        RenderPipelines.register(
            RenderPipeline.builder(TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/translucent_terrain"
                    )
                )
                .withColorTargetState(
                    new ColorTargetState(
                        BlendFunction.TRANSLUCENT
                    )
                )
                .withShaderDefine(
                    "ALPHA_CUTOUT",
                    0.1F
                )
                .build()
        );


    /*
     * ============================================================
     * BLOCK ITEMS
     * ============================================================
     *
     * Sampler0 = block atlas page 0
     * Sampler1 = vanilla overlay
     * Sampler2 = vanilla lightmap
     * Sampler3 = block atlas page 1
     * Sampler4 = block atlas page 2
     * Sampler5 = block atlas page 3
     */

    public static final BindGroupLayout ITEM_SPLIT_SAMPLERS =
        BindGroupLayout.builder()
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withSampler("Sampler3")
            .withSampler("Sampler4")
            .withSampler("Sampler5")
            .build();

    private static final Identifier ITEM_VERTEX_SHADER =
        Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            "core/item_split"
        );

    private static final Identifier ITEM_FRAGMENT_SHADER =
        Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            "core/item_split"
        );

    private static final RenderPipeline.Snippet ITEM =
        RenderPipeline.builder(
            RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET
        )
            .withVertexShader(
                ITEM_VERTEX_SHADER
            )
            .withFragmentShader(
                ITEM_FRAGMENT_SHADER
            )
            .withBindGroupLayout(
                ITEM_SPLIT_SAMPLERS
            )
            .withVertexBinding(
                0,
                DefaultVertexFormat.ENTITY
            )
            .withPrimitiveTopology(
                PrimitiveTopology.QUADS
            )
            .withDepthStencilState(
                DepthStencilState.DEFAULT
            )
            .buildSnippet();

    public static final RenderPipeline ITEM_CUTOUT =
        RenderPipelines.register(
            RenderPipeline.builder(ITEM)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/item_cutout"
                    )
                )
                .withShaderDefine(
                    "ALPHA_CUTOUT",
                    0.1F
                )
                .build()
        );

    public static final RenderPipeline ITEM_TRANSLUCENT =
        RenderPipelines.register(
            RenderPipeline.builder(ITEM)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/item_translucent"
                    )
                )
                .withShaderDefine(
                    "ALPHA_CUTOUT",
                    0.1F
                )
                .withColorTargetState(
                    new ColorTargetState(
                        BlendFunction.TRANSLUCENT
                    )
                )
                .build()
        );
}
