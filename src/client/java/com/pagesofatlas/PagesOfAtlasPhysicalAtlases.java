package com.pagesofatlas;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public final class PagesOfAtlasPhysicalAtlases {

    private PagesOfAtlasPhysicalAtlases() {}

    private static final Set<Identifier> REGISTERED =
        ConcurrentHashMap.newKeySet();

    private static final Map<Identifier, Set<Identifier>> OWNED =
        new ConcurrentHashMap<>();

    private static final Map<Identifier, Publication> PUBLISHED =
        new ConcurrentHashMap<>();

    public static void ensureRegistered(
        PagesOfAtlasRegistry.AtlasPlan plan
    ) {
        Set<Identifier> desired =
            new HashSet<>();

        for (PagesOfAtlasRegistry.PagePlan page : plan.pages()) {
            if (page.page() > 0) {
                desired.add(
                    page.physicalAtlas()
                );
            }
        }

        publish(
            plan.logicalAtlas(),
            Set.copyOf(desired),
            plan.generation()
        );
    }

    public static void publishVanillaAtlas(
        Identifier logicalAtlas,
        long generation
    ) {
        if (
            !OWNED.containsKey(logicalAtlas)
            && !PUBLISHED.containsKey(logicalAtlas)
        ) {
            return;
        }

        publish(
            logicalAtlas,
            Set.of(),
            generation
        );
    }

    private static void publish(
        Identifier logicalAtlas,
        Set<Identifier> desired,
        long generation
    ) {
        Publication publication =
            new Publication(
                generation,
                desired
            );

        PUBLISHED.put(
            logicalAtlas,
            publication
        );

        Minecraft minecraft =
            Minecraft.getInstance();

        minecraft.execute(() ->
            register(
                minecraft,
                logicalAtlas,
                publication
            )
        );
    }

    private static void register(
        Minecraft minecraft,
        Identifier logicalAtlas,
        Publication publication
    ) {
        if (
            !publication.equals(
                PUBLISHED.get(logicalAtlas)
            )
        ) {
            return;
        }

        TextureManager textureManager =
            minecraft.getTextureManager();

        for (
            Identifier physicalAtlas :
            publication.physicalAtlases()
        ) {
            if (!REGISTERED.add(physicalAtlas)) {
                continue;
            }

            try {
                TextureAtlas atlas =
                    new TextureAtlas(physicalAtlas);

                textureManager.register(
                    physicalAtlas,
                    atlas
                );

                trackOwned(
                    logicalAtlas,
                    physicalAtlas
                );

            } catch (Throwable t) {
                REGISTERED.remove(physicalAtlas);

                PagesOfAtlasClient.LOGGER.error(
                    "Failed to register physical PagesOfAtlas texture {}",
                    physicalAtlas,
                    t
                );
            }
        }
    }

    public static void completePagedAtlas(
        Identifier logicalAtlas,
        PagesOfAtlasRegistry.UploadBundle bundle
    ) {
        Set<Identifier> physicalAtlases =
            new HashSet<>();

        for (PagesOfAtlasRegistry.PageUpload page : bundle.pages()) {
            if (page.page() > 0) {
                physicalAtlases.add(
                    page.physicalAtlas()
                );
            }
        }

        complete(
            logicalAtlas,
            Set.copyOf(physicalAtlases),
            bundle.generation()
        );
    }

    public static void completeVanillaAtlas(
        Identifier logicalAtlas,
        long generation
    ) {
        complete(
            logicalAtlas,
            Set.of(),
            generation
        );
    }

    private static void complete(
        Identifier logicalAtlas,
        Set<Identifier> uploaded,
        long generation
    ) {
        Publication publication =
            PUBLISHED.get(logicalAtlas);

        if (
            publication == null
            || publication.generation()
                != generation
            || !publication.physicalAtlases()
                .equals(uploaded)
        ) {
            return;
        }

        Minecraft minecraft =
            Minecraft.getInstance();

        Runnable activate = () ->
            activate(
                minecraft,
                logicalAtlas,
                publication
            );

        if (RenderSystem.isOnRenderThread()) {
            activate.run();
            return;
        }

        minecraft.execute(() -> {
            RenderSystem.assertOnRenderThread();
            RenderSystem.queueFencedTask(activate);
        });
    }

    private static void activate(
        Minecraft minecraft,
        Identifier logicalAtlas,
        Publication publication
    ) {
        RenderSystem.assertOnRenderThread();

        if (
            !publication.equals(
                PUBLISHED.get(logicalAtlas)
            )
        ) {
            return;
        }

        Set<Identifier> desired =
            publication.physicalAtlases();

        TextureManager textureManager =
            minecraft.getTextureManager();

        Set<Identifier> owned =
            OWNED.getOrDefault(
                logicalAtlas,
                Set.of()
            );

        /*
         * A successful paged upload may have used TextureAtlasMixin's
         * fallback registration path. At this point every desired page
         * was uploaded successfully and is owned by TextureManager.
         */
        REGISTERED.addAll(desired);

        if (!desired.isEmpty()) {
            Set<Identifier> allOwned =
                new HashSet<>(owned);

            allOwned.addAll(desired);
            owned = Set.copyOf(allOwned);
        }

        for (Identifier physicalAtlas : owned) {
            if (desired.contains(physicalAtlas)) {
                continue;
            }

            textureManager.release(physicalAtlas);
            REGISTERED.remove(physicalAtlas);

            PagesOfAtlasClient.LOGGER.info(
                "Released obsolete PagesOfAtlas texture {}",
                physicalAtlas
            );
        }

        if (desired.isEmpty()) {
            OWNED.remove(logicalAtlas);
        } else {
            OWNED.put(
                logicalAtlas,
                desired
            );
        }

        PUBLISHED.remove(
            logicalAtlas,
            publication
        );
    }

    private static void trackOwned(
        Identifier logicalAtlas,
        Identifier physicalAtlas
    ) {
        OWNED.compute(
            logicalAtlas,
            (ignored, existing) -> {
                Set<Identifier> updated =
                    existing == null
                        ? new HashSet<>()
                        : new HashSet<>(existing);

                updated.add(physicalAtlas);
                return Set.copyOf(updated);
            }
        );
    }

    private record Publication(
        long generation,
        Set<Identifier> physicalAtlases
    ) {}
}
