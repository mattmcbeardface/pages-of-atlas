package com.pagesofatlas;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public final class PagesOfAtlasPhysicalAtlases {

    private PagesOfAtlasPhysicalAtlases() {}

    private static final Set<Identifier> REGISTERED =
        ConcurrentHashMap.newKeySet();

    public static void ensureRegistered(
        PagesOfAtlasRegistry.AtlasPlan plan
    ) {
        for (PagesOfAtlasRegistry.PagePlan page : plan.pages()) {

            // Page 0 is Minecraft's existing normal atlas.
            if (page.page() == 0) {
                continue;
            }

            Identifier physicalAtlas =
                page.physicalAtlas();

            if (!REGISTERED.add(physicalAtlas)) {
                continue;
            }

            Minecraft minecraft =
                Minecraft.getInstance();

            minecraft.execute(() -> {
                try {
                    TextureManager textureManager =
                        minecraft.getTextureManager();

                    TextureAtlas atlas =
                        new TextureAtlas(physicalAtlas);

                    textureManager.register(
                        physicalAtlas,
                        atlas
                    );

                } catch (Throwable t) {
                    REGISTERED.remove(physicalAtlas);

                    PagesOfAtlasClient.LOGGER.error(
                        "Failed to register physical PagesOfAtlas texture {}",
                        physicalAtlas,
                        t
                    );
                }
            });
        }
    }
}
