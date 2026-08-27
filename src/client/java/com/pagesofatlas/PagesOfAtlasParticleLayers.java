package com.pagesofatlas;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Cached particle layers for physical block-atlas pages.
 *
 * Particle UVs are already page-local. Giving each page its own layer lets
 * Minecraft's existing particle renderer bind that physical atlas directly
 * to Sampler0 without a particle-specific shader or vertex encoding.
 */
public final class PagesOfAtlasParticleLayers {

    private PagesOfAtlasParticleLayers() {}

    private static final ConcurrentMap<Key, SingleQuadParticle.Layer>
        LAYERS =
            new ConcurrentHashMap<>();

    public static SingleQuadParticle.Layer pageAware(
        SingleQuadParticle.Layer original,
        int page
    ) {
        if (page <= 0) {
            return original;
        }

        return LAYERS.computeIfAbsent(
            new Key(
                original,
                page
            ),
            PagesOfAtlasParticleLayers::create
        );
    }

    private static SingleQuadParticle.Layer create(
        Key key
    ) {
        SingleQuadParticle.Layer original =
            key.original();

        PagesOfAtlasClient.LOGGER.debug(
            "Created {} particle layer for block-atlas page {}",
            original.translucent()
                ? "translucent"
                : "opaque",
            key.page()
        );

        return new SingleQuadParticle.Layer(
            original.translucent(),
            PagesOfAtlasRegistry.physicalAtlasLocation(
                TextureAtlas.LOCATION_BLOCKS,
                key.page()
            ),
            original.pipeline()
        );
    }

    private record Key(
        SingleQuadParticle.Layer original,
        int page
    ) {}
}
