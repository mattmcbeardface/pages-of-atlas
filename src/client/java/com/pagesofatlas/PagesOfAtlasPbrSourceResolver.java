package com.pagesofatlas;

import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Resolves PBR companion images for logical atlas sprites.
 *
 * Example:
 *
 *   minecraft:block/gold_block
 *
 * becomes:
 *
 *   minecraft:textures/block/gold_block_n.png
 *   minecraft:textures/block/gold_block_s.png
 *
 * The important rule is that these images are NOT stitched
 * independently. Their final GPU placement will always come
 * directly from PagesOfAtlasRegistry.
 */
public final class PagesOfAtlasPbrSourceResolver {

    private PagesOfAtlasPbrSourceResolver() {}

    public enum Type {
        NORMAL("_n"),
        SPECULAR("_s");

        private final String suffix;

        Type(String suffix) {
            this.suffix = suffix;
        }

        public String suffix() {
            return suffix;
        }
    }

    public static Identifier imageLocation(
        Identifier sprite,
        Type type
    ) {
        return Identifier.fromNamespaceAndPath(
            sprite.getNamespace(),
            "textures/"
                + sprite.getPath()
                + type.suffix()
                + ".png"
        );
    }

    public static Optional<Resource> find(
        ResourceManager resourceManager,
        Identifier sprite,
        Type type
    ) {
        Identifier location =
            imageLocation(
                sprite,
                type
            );

        return resourceManager.getResource(
            location
        );
    }
}
