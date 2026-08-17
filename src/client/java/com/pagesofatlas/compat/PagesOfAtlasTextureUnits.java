package com.pagesofatlas.compat;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.opengl.GL33C;

/**
 * Reserves fragment texture units for:
 *
 * diffuse:
 *   page 1
 *   page 2
 *   page 3
 *
 * PBR:
 *   normal/specular pages 0-3
 *
 * Page 0 diffuse remains Sodium/Iris's ordinary block atlas sampler.
 */
public final class PagesOfAtlasTextureUnits {

    private static int maxUnits = -1;

    private PagesOfAtlasTextureUnits() {}

    public static int maxUnits() {
        if (maxUnits < 0) {
            maxUnits =
                GL33C.glGetInteger(
                    GL33C.GL_MAX_TEXTURE_IMAGE_UNITS
                );

            /*
             * We reserve eleven fragment units:
             *
             * 8 PBR
             * 3 diffuse
             */
            if (maxUnits < 16) {
                throw new IllegalStateException(
                    "PagesOfAtlas requires at least 16 fragment texture units; GPU reports "
                        + maxUnits
                );
            }
        }

        return maxUnits;
    }

    public static int normal0() {
        return maxUnits() - 11;
    }

    public static int specular0() {
        return maxUnits() - 10;
    }

    public static int normal1() {
        return maxUnits() - 9;
    }

    public static int specular1() {
        return maxUnits() - 8;
    }

    public static int normal2() {
        return maxUnits() - 7;
    }

    public static int specular2() {
        return maxUnits() - 6;
    }

    public static int normal3() {
        return maxUnits() - 5;
    }

    public static int specular3() {
        return maxUnits() - 4;
    }

    public static int page1() {
        return maxUnits() - 3;
    }

    public static int page2() {
        return maxUnits() - 2;
    }

    public static int page3() {
        return maxUnits() - 1;
    }

    public static Set<Integer> reserve(
        Set<Integer> original
    ) {
        Set<Integer> result =
            new HashSet<>(original);

        result.add(normal0());
        result.add(specular0());

        result.add(normal1());
        result.add(specular1());

        result.add(normal2());
        result.add(specular2());

        result.add(normal3());
        result.add(specular3());

        result.add(page1());
        result.add(page2());
        result.add(page3());

        return result;
    }
}
