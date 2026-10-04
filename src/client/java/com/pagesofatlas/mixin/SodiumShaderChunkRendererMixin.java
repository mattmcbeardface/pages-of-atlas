package com.pagesofatlas.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;

import net.minecraft.resources.Identifier;

import com.pagesofatlas.PagesOfAtlasVirtualAtlas;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer",
    remap = false
)
public abstract class SodiumShaderChunkRendererMixin {

    @Shadow
    @Final
    @Mutable
    public static BindGroupLayout BIND_GROUP;

    /*
     * Sodium normally exposes:
     *
     * u_LightTex
     * u_BlockTex
     *
     * PagesOfAtlas adds three additional physical block atlases.
     */
    @Inject(
        method = "<clinit>",
        at = @At("RETURN"),
        remap = false
    )
    private static void pagesofatlas$expandBindGroup(
        CallbackInfo ci
    ) {
        BIND_GROUP =
            BindGroupLayout.builder()
                .withUniform("u_BlockTex", UniformType.COMBINED_IMAGE_SAMPLER)

                .withUniform("u_BlockNormalTex0", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("u_BlockSpecularTex0", UniformType.COMBINED_IMAGE_SAMPLER)

                .withUniform("u_BlockNormalTex1", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("u_BlockSpecularTex1", UniformType.COMBINED_IMAGE_SAMPLER)

                .withUniform("u_BlockNormalTex2", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("u_BlockSpecularTex2", UniformType.COMBINED_IMAGE_SAMPLER)

                .withUniform("u_BlockNormalTex3", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("u_BlockSpecularTex3", UniformType.COMBINED_IMAGE_SAMPLER)

                .withUniform("u_BlockTex1", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("u_BlockTex2", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("u_BlockTex3", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(
                    "u_Globals",
                    com.mojang.renderpearl.api.pipeline.UniformType.UNIFORM_BUFFER
                )
                .withUniform(
                    "u_SectionTimeInfo",
                    com.mojang.renderpearl.api.pipeline.UniformType.TEXEL_BUFFER,
                    com.mojang.renderpearl.api.GpuFormat.R32_SINT
                )
                .build();
    }

    /*
     * Keep Sodium's terrain pipeline mechanics, but make it compile
     * PagesOfAtlas's page-aware terrain shaders.
     */
    @ModifyArg(
        method = {"createShader", "createOITShader"},
        at = @At(
            value = "INVOKE",
            target =
                "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withVertexShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"
        ),
        index = 0,
        remap = false
    )
    private Identifier pagesofatlas$vertexShader(
        Identifier original
    ) {
        return Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            PagesOfAtlasVirtualAtlas.active()
                ? "blocks/block_layer_virtual"
                : "blocks/block_layer_opaque"
        );
    }

    @ModifyArg(
        method = {"createShader", "createOITShader"},
        at = @At(
            value = "INVOKE",
            target =
                "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"
        ),
        index = 0,
        remap = false
    )
    private Identifier pagesofatlas$fragmentShader(
        Identifier original
    ) {
        return Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            PagesOfAtlasVirtualAtlas.active()
                ? "blocks/block_layer_virtual"
                : "blocks/block_layer_opaque"
        );
    }
}
