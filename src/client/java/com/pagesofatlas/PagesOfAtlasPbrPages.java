package com.pagesofatlas;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns POA-created PBR companion pages.
 *
 * Pages are lazy:
 *
 * render pass:
 *     request page N
 *     bind existing page N or fallback
 *
 * after render pass:
 *     build requested pages
 *
 * This keeps large texture uploads outside Sodium's active
 * RenderPass while allowing POA to support every physical page.
 */
public final class PagesOfAtlasPbrPages {

    private static final Map<Integer, PagesOfAtlasPbrPage>
        PAGES =
            new HashMap<>();

    private static final Set<Integer>
        REQUESTED =
            new HashSet<>();

    private static final Set<Integer>
        BUILDING =
            new HashSet<>();

    private static final Set<Integer>
        FAILED =
            new HashSet<>();

    private PagesOfAtlasPbrPages() {}

    public static synchronized boolean requestPage(
        int page
    ) {
        if (
            page <= 0
            || page > 3
            || FAILED.contains(page)
        ) {
            return false;
        }

        var planOptional =
            PagesOfAtlasRegistry.plan(
                TextureAtlas.LOCATION_BLOCKS
            );

        if (
            planOptional.isEmpty()
            || planOptional.get()
                .page(page)
                .isEmpty()
        ) {
            return false;
        }

        PagesOfAtlasPbrPage existing =
            PAGES.get(page);

        if (
            existing != null
            && existing.allocated()
        ) {
            return false;
        }

        REQUESTED.add(page);
        return true;
    }

    public static synchronized void buildRequestedPages() {

        if (REQUESTED.isEmpty()) {
            return;
        }

        /*
         * Work from a snapshot because successful builds remove
         * entries from REQUESTED.
         */
        Integer[] pages =
            REQUESTED.toArray(
                Integer[]::new
            );

        for (int page : pages) {
            buildPage(page);
        }
    }

    private static void buildPage(
        int page
    ) {
        PagesOfAtlasPbrPage existing =
            PAGES.get(page);

        if (
            existing != null
            && existing.allocated()
        ) {
            REQUESTED.remove(page);
            return;
        }

        if (BUILDING.contains(page)) {
            return;
        }

        var planOptional =
            PagesOfAtlasRegistry.plan(
                TextureAtlas.LOCATION_BLOCKS
            );

        if (planOptional.isEmpty()) {
            return;
        }

        var pageOptional =
            planOptional.get()
                .page(page);

        if (pageOptional.isEmpty()) {
            REQUESTED.remove(page);
            return;
        }

        var pagePlan =
            pageOptional.get();

        BUILDING.add(page);

        PagesOfAtlasPbrPage candidate =
            new PagesOfAtlasPbrPage(
                page,
                pagePlan.width(),
                pagePlan.height()
            );

        try {
            PagesOfAtlasClient.LOGGER.info(
                "[PBR PAGE] Building deterministic POA PBR page {}",
                page
            );

            PagesOfAtlasPbrUploader.uploadPage(
                candidate
            );

            PagesOfAtlasPbrPage previous =
                PAGES.put(
                    page,
                    candidate
                );

            if (
                previous != null
                && previous != candidate
            ) {
                previous.close();
            }

            REQUESTED.remove(page);

            PagesOfAtlasClient.LOGGER.info(
                "[PBR PAGE] Page {} ready",
                page
            );

        } catch (Throwable t) {
            REQUESTED.remove(page);
            FAILED.add(page);

            PagesOfAtlasClient.LOGGER.error(
                "[PBR PAGE] Failed building page {}",
                page,
                t
            );

            candidate.close();

        } finally {
            BUILDING.remove(page);
        }
    }

    public static synchronized GpuTextureView
        existingNormalPage(
            int page
        ) {

        PagesOfAtlasPbrPage existing =
            PAGES.get(page);

        if (
            existing == null
            || !existing.allocated()
        ) {
            return null;
        }

        return existing.normalView();
    }

    public static synchronized GpuTextureView
        existingSpecularPage(
            int page
        ) {

        PagesOfAtlasPbrPage existing =
            PAGES.get(page);

        if (
            existing == null
            || !existing.allocated()
        ) {
            return null;
        }

        return existing.specularView();
    }

    public static synchronized boolean
        isAllocated(
            int page
        ) {

        PagesOfAtlasPbrPage existing =
            PAGES.get(page);

        return
            existing != null
            && existing.allocated();
    }

    public static void clear() {
        List<PagesOfAtlasPbrPage> detached;

        synchronized (PagesOfAtlasPbrPages.class) {
            detached =
                List.copyOf(
                    PAGES.values()
                );

            PAGES.clear();
            REQUESTED.clear();
            BUILDING.clear();
            FAILED.clear();
        }

        closeDetached(detached);

        PagesOfAtlasClient.LOGGER.info(
            "[PBR PAGE] Cleared POA PBR pages"
        );
    }

    private static void closeDetached(
        List<PagesOfAtlasPbrPage> detached
    ) {
        if (detached.isEmpty()) {
            return;
        }

        Runnable close = () -> {
            RenderSystem.assertOnRenderThread();

            for (PagesOfAtlasPbrPage page : detached) {
                page.close();
            }
        };

        if (RenderSystem.isOnRenderThread()) {
            close.run();
            return;
        }

        Minecraft.getInstance().execute(() -> {
            RenderSystem.assertOnRenderThread();
            RenderSystem.queueFencedTask(close);
        });
    }
}
