package com.pagesofatlas.mixin;

import com.pagesofatlas.compat.PagesOfAtlasTextureUnits;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Iris dynamically assigns sampler texture units.
 *
 * Minecraft's Blaze3D pipeline independently assigns texture units
 * to PagesOfAtlas' u_BlockTex1/2/3 samplers. Without coordination,
 * those assignments can overlap Iris samplers such as gaux2,
 * gaux4, depthtex*, shadow textures, etc.
 *
 * Reserve the last three fragment texture units inside Iris'
 * allocator. GlProgramMixin assigns the PagesOfAtlas page samplers
 * to exactly these same three units.
 */
@Pseudo
@Mixin(
    targets =
        "net.irisshaders.iris.gl.program.ProgramSamplers$Builder",
    remap = false
)
public abstract class IrisProgramSamplersMixin {

    /*
     * IMPORTANT:
     *
     * This handler MUST be static because the injection occurs at
     * HEAD of a constructor, before Object.<init>() has completed.
     *
     * Constructor:
     *   Builder(int program, Set<Integer> reservedTextureUnits)
     *
     * Local indices:
     *   0 = this
     *   1 = program
     *   2 = reservedTextureUnits
     */
    @ModifyVariable(
        method = "<init>",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2,
        remap = false
    )
    private static Set<Integer>
        pagesofatlas$reservePageTextureUnits(
            Set<Integer> original
        ) {

        return PagesOfAtlasTextureUnits.reserve(
            original
        );
    }
}
