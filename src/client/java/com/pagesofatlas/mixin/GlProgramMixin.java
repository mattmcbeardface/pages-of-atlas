package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasClient;
import com.pagesofatlas.compat.PagesOfAtlasTextureUnits;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.Uniform;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlProgram.class)
public abstract class GlProgramMixin {

    @Shadow
    @Final
    private Map<String, Uniform> uniformsByName;

    @Shadow
    @Final
    private int programId;

    @Inject(
        method = "setupBindGroupLayouts",
        at = @At("RETURN")
    )
    private void pagesofatlas$assignPageSamplerUnits(
        CallbackInfo ci
    ) {
        pagesofatlas$moveSampler(
            "u_BlockNormalTex0",
            PagesOfAtlasTextureUnits.normal0()
        );

        pagesofatlas$moveSampler(
            "u_BlockSpecularTex0",
            PagesOfAtlasTextureUnits.specular0()
        );

        pagesofatlas$moveSampler(
            "u_BlockNormalTex1",
            PagesOfAtlasTextureUnits.normal1()
        );

        pagesofatlas$moveSampler(
            "u_BlockSpecularTex1",
            PagesOfAtlasTextureUnits.specular1()
        );

        pagesofatlas$moveSampler(
            "u_BlockNormalTex2",
            PagesOfAtlasTextureUnits.normal2()
        );

        pagesofatlas$moveSampler(
            "u_BlockSpecularTex2",
            PagesOfAtlasTextureUnits.specular2()
        );

        pagesofatlas$moveSampler(
            "u_BlockNormalTex3",
            PagesOfAtlasTextureUnits.normal3()
        );

        pagesofatlas$moveSampler(
            "u_BlockSpecularTex3",
            PagesOfAtlasTextureUnits.specular3()
        );

        pagesofatlas$moveSampler(
            "u_BlockTex1",
            PagesOfAtlasTextureUnits.page1()
        );

        pagesofatlas$moveSampler(
            "u_BlockTex2",
            PagesOfAtlasTextureUnits.page2()
        );

        pagesofatlas$moveSampler(
            "u_BlockTex3",
            PagesOfAtlasTextureUnits.page3()
        );

        /*
         * POA's standalone item shaders expose their overflow pages as
         * Sampler3/4/5.  Once Iris correctly overrides those pipelines,
         * the active program instead exposes the routed sampler names
         * injected into the shader-pack program.  Alias the pipeline's
         * binding names to those uniforms so the existing RenderSetup
         * binds the same three textures to Iris's reserved units.
         */
        pagesofatlas$aliasSampler(
            "Sampler3",
            "u_BlockTex1",
            PagesOfAtlasTextureUnits.page1()
        );

        pagesofatlas$aliasSampler(
            "Sampler4",
            "u_BlockTex2",
            PagesOfAtlasTextureUnits.page2()
        );

        pagesofatlas$aliasSampler(
            "Sampler5",
            "u_BlockTex3",
            PagesOfAtlasTextureUnits.page3()
        );
    }

    private void pagesofatlas$moveSampler(
        String name,
        int textureUnit
    ) {
        Uniform uniform =
            this.uniformsByName.get(name);

        Uniform.Sampler sampler;

        if (uniform instanceof Uniform.Sampler existing) {
            sampler = existing;
        } else {
            int location =
                GlStateManager._glGetUniformLocation(
                    this.programId,
                    name
                );

            if (location < 0) {
                return;
            }

            sampler =
                new Uniform.Sampler(
                    location,
                    textureUnit
                );
        }

        this.uniformsByName.put(
            name,
            new Uniform.Sampler(
                sampler.location(),
                textureUnit
            )
        );

        PagesOfAtlasClient.LOGGER.debug(
            "Assigned {} to reserved GL texture unit {}",
            name,
            textureUnit
        );
    }

    private void pagesofatlas$aliasSampler(
        String alias,
        String target,
        int textureUnit
    ) {
        Uniform uniform =
            this.uniformsByName.get(target);

        if (!(uniform instanceof Uniform.Sampler sampler)) {
            return;
        }

        this.uniformsByName.put(
            alias,
            new Uniform.Sampler(
                sampler.location(),
                textureUnit
            )
        );
    }
}
