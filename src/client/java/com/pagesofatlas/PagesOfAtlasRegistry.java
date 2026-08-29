package com.pagesofatlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.resources.Identifier;

public final class PagesOfAtlasRegistry {

    private PagesOfAtlasRegistry() {}

    private static final ThreadLocal<Identifier> CURRENT_ATLAS =
        new ThreadLocal<>();

    private static final ThreadLocal<Long> CURRENT_GENERATION =
        new ThreadLocal<>();

    private static final Map<Identifier, Long> GENERATIONS =
        new ConcurrentHashMap<>();

    private static final Map<Identifier, AtlasPlan> STAGED_PLANS =
        new ConcurrentHashMap<>();

    private static final Map<Identifier, ActiveAtlas> ACTIVE_ATLASES =
        new ConcurrentHashMap<>();

    private static final Map<SpriteKey, Placement> PLACEMENTS =
        new ConcurrentHashMap<>();

    private static final Map<SpriteKey, SpriteDimensions> DIMENSIONS =
        new ConcurrentHashMap<>();

    private static final Map<Identifier, UploadBundle> UPLOADS =
        new ConcurrentHashMap<>();

    private static final Map<SpriteLoader.Preparations, VanillaUpload>
        VANILLA_UPLOADS =
            Collections.synchronizedMap(
                new IdentityHashMap<>()
            );

    public static void beginAtlas(
        Identifier atlas
    ) {
        synchronized (PagesOfAtlasRegistry.class) {
            long generation =
                GENERATIONS.merge(
                    atlas,
                    1L,
                    Long::sum
                );

            CURRENT_ATLAS.set(atlas);
            CURRENT_GENERATION.set(generation);

            clearStagedAtlas(atlas);
        }
    }

    public static void endAtlas() {
        CURRENT_ATLAS.remove();
        CURRENT_GENERATION.remove();
    }

    public static Identifier currentAtlas() {
        return CURRENT_ATLAS.get();
    }

    public static long currentGeneration() {
        Long generation =
            CURRENT_GENERATION.get();

        if (generation == null) {
            throw new IllegalStateException(
                "No PagesOfAtlas generation is being prepared"
            );
        }

        return generation;
    }

    private static void clearStagedAtlas(
        Identifier atlas
    ) {
        STAGED_PLANS.remove(atlas);
        UPLOADS.remove(atlas);

        PLACEMENTS.keySet().removeIf(
            key -> key.atlas().equals(atlas)
        );

        DIMENSIONS.keySet().removeIf(
            key -> key.atlas().equals(atlas)
        );
    }

    public static <T extends Stitcher.Entry> void publishCurrent(
        PagesOfAtlasPager.Result<T> result
    ) {
        Identifier atlas =
            CURRENT_ATLAS.get();

        if (atlas == null) {
            PagesOfAtlasClient.LOGGER.warn(
                "Pages of Atlas result produced without an active atlas"
            );
            return;
        }

        List<PagePlan> pagePlans =
            new ArrayList<>();

        int spriteCount = 0;

        for (
            PagesOfAtlasPager.Page<T> page :
            result.pages()
        ) {
            List<Identifier> sprites =
                new ArrayList<>();

            for (
                PagesOfAtlasPager.Placement<T> placement :
                page.placements()
            ) {
                Identifier sprite =
                    placement.name();

                sprites.add(sprite);

                Placement previous =
                    PLACEMENTS.putIfAbsent(
                        new SpriteKey(
                            atlas,
                            sprite
                        ),
                        new Placement(
                            placement.page(),
                            page.width(),
                            page.height(),
                            placement.x(),
                            placement.y(),
                            placement.padding()
                        )
                    );

                /*
                 * Replicated physical entries, currently minecraft:back
                 * on painting pages, remain one logical sprite. Preserve
                 * the first/page-zero placement for logical lookup and
                 * count the identifier only once.
                 */
                if (previous == null) {
                    spriteCount++;
                }
            }

            pagePlans.add(
                new PagePlan(
                    page.number(),
                    page.width(),
                    page.height(),
                    physicalAtlasLocation(
                        atlas,
                        page.number()
                    ),
                    List.copyOf(sprites)
                )
            );
        }

        AtlasPlan plan =
            new AtlasPlan(
                atlas,
                List.copyOf(pagePlans),
                spriteCount,
                currentGeneration()
            );

        STAGED_PLANS.put(
            atlas,
            plan
        );

        PagesOfAtlasClient.LOGGER.debug(
            "Pages of Atlas staged: {} -> {} pages, {} sprites",
            atlas,
            pagePlans.size(),
            spriteCount
        );
        for (
            PagePlan page :
            pagePlans
        ) {
            PagesOfAtlasClient.LOGGER.debug(
                "Atlas page {}: {} ({}x{}, {} sprites)",
                page.page(),
                page.physicalAtlas(),
                page.width(),
                page.height(),
                page.sprites().size()
            );
        }

        PagesOfAtlasPhysicalAtlases.ensureRegistered(
            plan
        );
    }

    public static void publishUploadBundle(
        Identifier logicalAtlas,
        UploadBundle bundle
    ) {
        UPLOADS.put(
            logicalAtlas,
            bundle
        );
    }

    public static Optional<UploadBundle> uploadBundle(
        Identifier logicalAtlas
    ) {
        return Optional.ofNullable(
            UPLOADS.get(logicalAtlas)
        );
    }

    public static boolean isStagedGeneration(
        Identifier logicalAtlas,
        long generation
    ) {
        AtlasPlan staged =
            STAGED_PLANS.get(logicalAtlas);

        return
            staged != null
            && staged.generation() == generation
            && GENERATIONS.getOrDefault(
                logicalAtlas,
                -1L
            ) == generation;
    }

    public static Optional<AtlasPlan> plan(
        Identifier logicalAtlas
    ) {
        ActiveAtlas active =
            ACTIVE_ATLASES.get(logicalAtlas);

        return active == null
            ? Optional.empty()
            : Optional.of(active.plan());
    }

    public static Optional<Placement> lookup(
        Identifier atlas,
        Identifier sprite
    ) {
        SpriteKey key =
            new SpriteKey(
                atlas,
                sprite
            );

        Placement staged =
            PLACEMENTS.get(key);

        if (atlas.equals(CURRENT_ATLAS.get())) {
            return Optional.ofNullable(staged);
        }

        ActiveAtlas active =
            ACTIVE_ATLASES.get(atlas);

        return active == null
            ? Optional.empty()
            : Optional.ofNullable(
                active.placements().get(key)
            );
    }

    public static void recordSpriteDimensions(
        Identifier atlas,
        Identifier sprite,
        int width,
        int height
    ) {
        DIMENSIONS.put(
            new SpriteKey(
                atlas,
                sprite
            ),
            new SpriteDimensions(
                width,
                height
            )
        );
    }

    public static Optional<SpriteDimensions> spriteDimensions(
        Identifier atlas,
        Identifier sprite
    ) {
        ActiveAtlas active =
            ACTIVE_ATLASES.get(atlas);

        if (active == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
            active.dimensions().get(
                new SpriteKey(
                    atlas,
                    sprite
                )
            )
        );
    }

    public static List<PlacedSprite> placementsForPage(
        Identifier atlas,
        int page
    ) {
        List<PlacedSprite> result =
            new ArrayList<>();

        ActiveAtlas active =
            ACTIVE_ATLASES.get(atlas);

        if (active == null) {
            return List.of();
        }

        for (
            Map.Entry<SpriteKey, Placement> entry :
            active.placements().entrySet()
        ) {
            SpriteKey key =
                entry.getKey();

            Placement placement =
                entry.getValue();

            if (
                key.atlas().equals(atlas)
                && placement.page() == page
            ) {
                result.add(
                    new PlacedSprite(
                        key.sprite(),
                        placement
                    )
                );
            }
        }

        result.sort(
            java.util.Comparator.comparing(
                placed ->
                    placed.sprite()
                        .toString()
            )
        );

        return List.copyOf(result);
    }

    public static void stageVanillaUpload(
        Identifier logicalAtlas,
        SpriteLoader.Preparations preparations
    ) {
        long generation =
            currentGeneration();

        VANILLA_UPLOADS.put(
            preparations,
            new VanillaUpload(
                logicalAtlas,
                generation
            )
        );

        PagesOfAtlasPhysicalAtlases.publishVanillaAtlas(
            logicalAtlas,
            generation
        );
    }

    public static boolean activatePaged(
        Identifier logicalAtlas,
        UploadBundle bundle
    ) {
        RenderSystem.assertOnRenderThread();

        AtlasPlan staged;

        synchronized (PagesOfAtlasRegistry.class) {
            staged =
                STAGED_PLANS.get(logicalAtlas);

            if (
                staged == null
                || staged.generation()
                    != bundle.generation()
                || GENERATIONS.getOrDefault(
                    logicalAtlas,
                    -1L
                ) != bundle.generation()
            ) {
                return false;
            }

            ACTIVE_ATLASES.put(
                logicalAtlas,
                new ActiveAtlas(
                    staged,
                    stagedPlacements(logicalAtlas),
                    stagedDimensions(logicalAtlas)
                )
            );

            STAGED_PLANS.remove(
                logicalAtlas,
                staged
            );
        }

        if (
            logicalAtlas.equals(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS
            )
        ) {
            PagesOfAtlasPbrPages.clear();
        }

        PagesOfAtlasClient.LOGGER.info(
            "Pages of Atlas active: {} -> {} pages, {} sprites (generation {})",
            logicalAtlas,
            staged.pageCount(),
            staged.spriteCount(),
            staged.generation()
        );

        return true;
    }

    public static long activateVanilla(
        Identifier logicalAtlas,
        SpriteLoader.Preparations preparations
    ) {
        RenderSystem.assertOnRenderThread();

        VanillaUpload upload =
            VANILLA_UPLOADS.remove(preparations);

        if (
            upload == null
            || !upload.logicalAtlas()
                .equals(logicalAtlas)
        ) {
            return -1L;
        }

        synchronized (PagesOfAtlasRegistry.class) {
            if (
                GENERATIONS.getOrDefault(
                    logicalAtlas,
                    -1L
                ) != upload.generation()
            ) {
                return -1L;
            }

            ACTIVE_ATLASES.remove(logicalAtlas);
            STAGED_PLANS.remove(logicalAtlas);
        }

        if (
            logicalAtlas.equals(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS
            )
        ) {
            PagesOfAtlasPbrPages.clear();
        }

        return upload.generation();
    }

    private static Map<SpriteKey, Placement> stagedPlacements(
        Identifier logicalAtlas
    ) {
        Map<SpriteKey, Placement> result =
            new HashMap<>();

        for (Map.Entry<SpriteKey, Placement> entry : PLACEMENTS.entrySet()) {
            if (entry.getKey().atlas().equals(logicalAtlas)) {
                result.put(
                    entry.getKey(),
                    entry.getValue()
                );
            }
        }

        return Map.copyOf(result);
    }

    private static Map<SpriteKey, SpriteDimensions> stagedDimensions(
        Identifier logicalAtlas
    ) {
        Map<SpriteKey, SpriteDimensions> result =
            new HashMap<>();

        for (
            Map.Entry<SpriteKey, SpriteDimensions> entry :
            DIMENSIONS.entrySet()
        ) {
            if (entry.getKey().atlas().equals(logicalAtlas)) {
                result.put(
                    entry.getKey(),
                    entry.getValue()
                );
            }
        }

        return Map.copyOf(result);
    }

    public static Identifier physicalAtlasLocation(
        Identifier logicalAtlas,
        int page
    ) {
        if (page == 0) {
            return logicalAtlas;
        }

        String path =
            logicalAtlas.getPath();

        int extension =
            path.lastIndexOf('.');

        String base =
            extension >= 0
                ? path.substring(
                    0,
                    extension
                )
                : path;

        String suffix =
            extension >= 0
                ? path.substring(
                    extension
                )
                : "";

        return Identifier.fromNamespaceAndPath(
            "pagesofatlas",
            base
                + "_page_"
                + page
                + suffix
        );
    }

    public record Placement(
        int page,
        int pageWidth,
        int pageHeight,
        int x,
        int y,
        int padding
    ) {}

    public record SpriteDimensions(
        int width,
        int height
    ) {}

    public record PlacedSprite(
        Identifier sprite,
        Placement placement
    ) {}

    public record PagePlan(
        int page,
        int width,
        int height,
        Identifier physicalAtlas,
        List<Identifier> sprites
    ) {}

    public record AtlasPlan(
        Identifier logicalAtlas,
        List<PagePlan> pages,
        int spriteCount,
        long generation
    ) {
        public int pageCount() {
            return pages.size();
        }

        public Optional<PagePlan> page(
            int pageNumber
        ) {
            return pages.stream()
                .filter(
                    page ->
                        page.page()
                            == pageNumber
                )
                .findFirst();
        }
    }

    public record PageUpload(
        int page,
        Identifier physicalAtlas,
        SpriteLoader.Preparations preparations
    ) {}

    public record UploadBundle(
        SpriteLoader.Preparations combined,
        List<PageUpload> pages,
        long generation
    ) {}

    private record ActiveAtlas(
        AtlasPlan plan,
        Map<SpriteKey, Placement> placements,
        Map<SpriteKey, SpriteDimensions> dimensions
    ) {}

    private record VanillaUpload(
        Identifier logicalAtlas,
        long generation
    ) {}

    private record SpriteKey(
        Identifier atlas,
        Identifier sprite
    ) {}
}
