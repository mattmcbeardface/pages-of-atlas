package com.pagesofatlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.resources.Identifier;

public final class PagesOfAtlasRegistry {

    private PagesOfAtlasRegistry() {}

    private static final ThreadLocal<Identifier> CURRENT_ATLAS =
        new ThreadLocal<>();

    private static final Map<Identifier, AtlasPlan> PLANS =
        new ConcurrentHashMap<>();

    private static final Map<SpriteKey, Placement> PLACEMENTS =
        new ConcurrentHashMap<>();

    private static final Map<SpriteKey, SpriteDimensions> DIMENSIONS =
        new ConcurrentHashMap<>();

    private static final Map<Identifier, UploadBundle> UPLOADS =
        new ConcurrentHashMap<>();

    public static void beginAtlas(
        Identifier atlas
    ) {
        CURRENT_ATLAS.set(atlas);

        /*
         * Every atlas rebuild starts clean.
         *
         * This matters when the user changes resource packs
         * without restarting Minecraft. An atlas that needed
         * paging during the previous reload may no longer need
         * it during the next one.
         */
        clearAtlas(atlas);
    }

    public static void endAtlas() {
        CURRENT_ATLAS.remove();
    }

    public static Identifier currentAtlas() {
        return CURRENT_ATLAS.get();
    }

    public static void clearAtlas(
        Identifier atlas
    ) {
        PLANS.remove(atlas);
        UPLOADS.remove(atlas);

        if (
            atlas.equals(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS
            )
        ) {
            PagesOfAtlasPbrPages.clear();
        }

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

                PLACEMENTS.put(
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

                spriteCount++;
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
                spriteCount
            );

        PLANS.put(
            atlas,
            plan
        );

        PagesOfAtlasClient.LOGGER.info(
            "Pages of Atlas active: {} -> {} pages, {} sprites",
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

        CURRENT_ATLAS.remove();
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

    public static Optional<AtlasPlan> plan(
        Identifier logicalAtlas
    ) {
        return Optional.ofNullable(
            PLANS.get(logicalAtlas)
        );
    }

    public static Optional<Placement> lookup(
        Identifier atlas,
        Identifier sprite
    ) {
        return Optional.ofNullable(
            PLACEMENTS.get(
                new SpriteKey(
                    atlas,
                    sprite
                )
            )
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
        return Optional.ofNullable(
            DIMENSIONS.get(
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

        for (
            Map.Entry<SpriteKey, Placement> entry :
            PLACEMENTS.entrySet()
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
        int spriteCount
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
        List<PageUpload> pages
    ) {}

    private record SpriteKey(
        Identifier atlas,
        Identifier sprite
    ) {}
}
