package com.pagesofatlas;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
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
            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Sampler2", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Sampler3", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Sampler4", UniformType.COMBINED_IMAGE_SAMPLER)
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

    private static final Identifier VIRTUAL_TERRAIN_SHADER =
        Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            "core/terrain_virtual"
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
            .withBindGroupLayout(
                BindGroupLayouts.TERRAIN_INFO
            )
            .withVertexShader(
                TERRAIN_VERTEX_SHADER
            )
            .withFragmentShader(
                TERRAIN_FRAGMENT_SHADER
            )
            .buildSnippet();

    private static final RenderPipeline.Snippet MULTIDRAW_TERRAIN =
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
            .withVertexBinding(
                1,
                DefaultVertexFormat.CHUNK_DATA_INSTANCED
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
                BindGroupLayouts.TERRAIN_INFO
            )
            .withVertexShader(
                TERRAIN_VERTEX_SHADER
            )
            .withFragmentShader(
                TERRAIN_FRAGMENT_SHADER
            )
            .withShaderDefine(
                "MULTIDRAW_TERRAIN"
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

    public static final RenderPipeline SOLID_MULTIDRAW =
        RenderPipelines.register(
            RenderPipeline.builder(MULTIDRAW_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/solid_terrain_multidraw"
                    )
                )
                .build()
        );

    public static final RenderPipeline CUTOUT_MULTIDRAW =
        RenderPipelines.register(
            RenderPipeline.builder(MULTIDRAW_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/cutout_terrain_multidraw"
                    )
                )
                .withShaderDefine(
                    "ALPHA_CUTOUT",
                    0.5F
                )
                .build()
        );

    public static final RenderPipeline TRANSLUCENT_MULTIDRAW =
        RenderPipelines.register(
            RenderPipeline.builder(MULTIDRAW_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/translucent_terrain_multidraw"
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

    private static final RenderPipeline.Snippet VIRTUAL_TERRAIN =
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
            .withBindGroupLayout(
                BindGroupLayouts.TERRAIN_INFO
            )
            .withVertexShader(
                VIRTUAL_TERRAIN_SHADER
            )
            .withFragmentShader(
                VIRTUAL_TERRAIN_SHADER
            )
            .buildSnippet();

    private static final RenderPipeline.Snippet VIRTUAL_MULTIDRAW_TERRAIN =
        RenderPipeline.builder(MULTIDRAW_TERRAIN)
            .withVertexShader(
                VIRTUAL_TERRAIN_SHADER
            )
            .withFragmentShader(
                VIRTUAL_TERRAIN_SHADER
            )
            .buildSnippet();

    public static final RenderPipeline VIRTUAL_SOLID =
        RenderPipelines.register(
            RenderPipeline.builder(VIRTUAL_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/virtual_solid_terrain"
                    )
                )
                .build()
        );

    public static final RenderPipeline VIRTUAL_CUTOUT =
        RenderPipelines.register(
            RenderPipeline.builder(VIRTUAL_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/virtual_cutout_terrain"
                    )
                )
                .withShaderDefine(
                    "ALPHA_CUTOUT",
                    0.5F
                )
                .build()
        );

    public static final RenderPipeline VIRTUAL_TRANSLUCENT =
        RenderPipelines.register(
            RenderPipeline.builder(VIRTUAL_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/virtual_translucent_terrain"
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

    public static final RenderPipeline VIRTUAL_SOLID_MULTIDRAW =
        RenderPipelines.register(
            RenderPipeline.builder(VIRTUAL_MULTIDRAW_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/virtual_solid_terrain_multidraw"
                    )
                )
                .build()
        );

    public static final RenderPipeline VIRTUAL_CUTOUT_MULTIDRAW =
        RenderPipelines.register(
            RenderPipeline.builder(VIRTUAL_MULTIDRAW_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/virtual_cutout_terrain_multidraw"
                    )
                )
                .withShaderDefine(
                    "ALPHA_CUTOUT",
                    0.5F
                )
                .build()
        );

    public static final RenderPipeline VIRTUAL_TRANSLUCENT_MULTIDRAW =
        RenderPipelines.register(
            RenderPipeline.builder(VIRTUAL_MULTIDRAW_TERRAIN)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "pagesofatlas",
                        "pipeline/virtual_translucent_terrain_multidraw"
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

    public static final OitPipelineSet OIT_TERRAIN =
        RenderPipelines.register(
            OitPipelineSet.builder(
                "pagesofatlas_terrain",
                RenderPipeline.builder(TERRAIN)
                    .withLocation(
                        Identifier.fromNamespaceAndPath(
                            "pagesofatlas",
                            "pipeline/oit_terrain"
                        )
                    )
                    .withShaderDefine(
                        "ALPHA_CUTOUT",
                        0.1F
                    )
            ).build()
        );

    public static final OitPipelineSet OIT_TERRAIN_MULTIDRAW =
        RenderPipelines.register(
            OitPipelineSet.builder(
                "pagesofatlas_terrain_multidraw",
                RenderPipeline.builder(MULTIDRAW_TERRAIN)
                    .withLocation(
                        Identifier.fromNamespaceAndPath(
                            "pagesofatlas",
                            "pipeline/oit_terrain_multidraw"
                        )
                    )
                    .withShaderDefine(
                        "ALPHA_CUTOUT",
                        0.1F
                    )
            ).build()
        );

    public static final OitPipelineSet VIRTUAL_OIT_TERRAIN =
        RenderPipelines.register(
            OitPipelineSet.builder(
                "pagesofatlas_virtual_terrain",
                RenderPipeline.builder(VIRTUAL_TERRAIN)
                    .withLocation(
                        Identifier.fromNamespaceAndPath(
                            "pagesofatlas",
                            "pipeline/virtual_oit_terrain"
                        )
                    )
                    .withShaderDefine(
                        "ALPHA_CUTOUT",
                        0.1F
                    )
            ).build()
        );

    public static final OitPipelineSet VIRTUAL_OIT_TERRAIN_MULTIDRAW =
        RenderPipelines.register(
            OitPipelineSet.builder(
                "pagesofatlas_virtual_terrain_multidraw",
                RenderPipeline.builder(VIRTUAL_MULTIDRAW_TERRAIN)
                    .withLocation(
                        Identifier.fromNamespaceAndPath(
                            "pagesofatlas",
                            "pipeline/virtual_oit_terrain_multidraw"
                        )
                    )
                    .withShaderDefine(
                        "ALPHA_CUTOUT",
                        0.1F
                    )
            ).build()
        );


    /*
     * ============================================================
     * BLOCK ITEMS
     * ============================================================
     *
     * Sampler0 = the physical block-atlas page selected for this draw
     * Sampler1 = vanilla overlay
     * Sampler2 = vanilla lightmap
     *
     * This deliberately matches Minecraft's ordinary item pipeline. Iris can
     * therefore reuse its normal item program without any PoA shader
     * transformation; the originating PoA pipeline identity only selects the
     * correct semantic override.
     */

    /*
     * Keep this derived from Minecraft's item snippet rather than copying
     * the superficially similar entity pipeline. GUI and hotbar items are
     * rendered into GuiItemAtlas outside Iris's level-rendering scope, so
     * this native fallback must preserve the item shader and its complete
     * Sampler0/Sampler1/Sampler2 contract on its own.
     */
    private static final RenderPipeline.Snippet ITEM =
        RenderPipeline.builder(
            RenderPipelines.ITEM_SNIPPET
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
