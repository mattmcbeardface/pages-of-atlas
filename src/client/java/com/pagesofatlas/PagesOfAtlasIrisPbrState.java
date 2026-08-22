package com.pagesofatlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Reads Iris's actual block-atlas PBR state without creating it.
 */
public final class PagesOfAtlasIrisPbrState {

    private PagesOfAtlasIrisPbrState() {}

    public static boolean active() {
        AbstractTexture blockTexture =
            Minecraft.getInstance()
                .getTextureManager()
                .getTexture(
                    TextureAtlas.LOCATION_BLOCKS
                );

        if (blockTexture == null) {
            return false;
        }

        try {
            Class<?> extensionClass =
                Class.forName(
                    "net.irisshaders.iris.pbr.texture.TextureAtlasExtension"
                );

            if (!extensionClass.isInstance(blockTexture)) {
                return false;
            }

            Object holder =
                extensionClass
                    .getMethod("getPBRHolder")
                    .invoke(blockTexture);

            if (holder == null) {
                return false;
            }

            Object normal =
                holder.getClass()
                    .getMethod("getNormalAtlas")
                    .invoke(holder);

            Object specular =
                holder.getClass()
                    .getMethod("getSpecularAtlas")
                    .invoke(holder);

            return normal != null || specular != null;

        } catch (
            ReflectiveOperationException
            | LinkageError ignored
        ) {
            return false;
        }
    }
}
