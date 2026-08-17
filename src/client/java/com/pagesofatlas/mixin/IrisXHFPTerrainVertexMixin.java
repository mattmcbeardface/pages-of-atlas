package com.pagesofatlas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/*
 * Iris + Sodium terrain compatibility.
 *
 * Iris's XHFP encoder receives Sodium's material bits as the
 * second argument to write(...).
 *
 * PagesOfAtlas stores the physical atlas page in material bits 3-4.
 *
 * Iris normally discards those material bits when constructing
 * a_LightAndData. Preserve PagesOfAtlas bits 3-4 inside the otherwise
 * available bits of a_LightAndData.z.
 *
 * Result:
 *
 * a_LightAndData.z bit 0   = Iris tangent sign
 * a_LightAndData.z bits 3-4 = PagesOfAtlas page
 */
@Pseudo
@Mixin(
    targets =
        "net.irisshaders.iris.vertices.sodium.terrain.XHFPTerrainVertex",
    remap = false
)
public abstract class IrisXHFPTerrainVertexMixin {

    @Redirect(
        method = "write",
        at = @At(
            value = "INVOKE",
            target =
                "Lnet/irisshaders/iris/vertices/sodium/terrain/XHFPTerrainVertex;packLightAndData(IZI)I"
        ),
        remap = false
    )
    private int pagesofatlas$preservePageBits(
        int light,
        boolean tangentPositive,
        int sectionIndex,
        long address,
        int materialBits,
        @Coerce Object[] vertices,
        int writeSectionIndex
    ) {
        int packed =
            (light & 0xFFFF)
            | ((tangentPositive ? 1 : 0) << 16)
            | ((sectionIndex & 0xFF) << 24);

        /*
         * packLightAndData() writes a_LightAndData as one 32-bit
         * integer:
         *
         * bits  0-15 = light
         * bits 16-23 = a_LightAndData.z
         * bits 24-31 = a_LightAndData.w
         *
         * Sodium material bits 3-4 therefore need to move into
         * bits 19-20 of this packed integer.
         */
        packed |=
            (materialBits & 0x18) << 16;

        return packed;
    }
}
