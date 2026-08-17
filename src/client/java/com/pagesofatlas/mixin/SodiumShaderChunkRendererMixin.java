package com.pagesofatlas.mixin;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.resources.Identifier;

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
                .withSampler("u_LightTex")
                .withSampler("u_BlockTex")

                .withSampler("u_BlockNormalTex0")
                .withSampler("u_BlockSpecularTex0")

                .withSampler("u_BlockNormalTex1")
                .withSampler("u_BlockSpecularTex1")

                .withSampler("u_BlockNormalTex2")
                .withSampler("u_BlockSpecularTex2")

                .withSampler("u_BlockNormalTex3")
                .withSampler("u_BlockSpecularTex3")

                .withSampler("u_BlockTex1")
                .withSampler("u_BlockTex2")
                .withSampler("u_BlockTex3")
                .withUniform(
                    "u_Globals",
                    com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER
                )
                .withUniform(
                    "u_SectionTimeInfo",
                    com.mojang.blaze3d.shaders.UniformType.TEXEL_BUFFER,
                    com.mojang.blaze3d.GpuFormat.R32_SINT
                )
                .build();
    }

    /*
     * Keep Sodium's terrain pipeline mechanics, but make it compile
     * PagesOfAtlas's page-aware terrain shaders.
     */
    @ModifyArg(
        method = "createShader",
        at = @At(
            value = "INVOKE",
            target =
                "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withVertexShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"
        ),
        index = 0,
        remap = false
    )
    private Identifier pagesofatlas$vertexShader(
        Identifier original
    ) {
        return Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            "blocks/block_layer_opaque"
        );
    }

    @ModifyArg(
        method = "createShader",
        at = @At(
            value = "INVOKE",
            target =
                "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withFragmentShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"
        ),
        index = 0,
        remap = false
    )
    private Identifier pagesofatlas$fragmentShader(
        Identifier original
    ) {
        return Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            "blocks/block_layer_opaque"
        );
    }
}
