package com.pagesofatlas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class PagesOfAtlasPager {

    private PagesOfAtlasPager() {}

    public static <T extends Stitcher.Entry> Result<T> pack(
        List<T> input,
        int maxWidth,
        int maxHeight,
        int mipLevel,
        int padding
    ) {
        return pack(
            input,
            maxWidth,
            maxHeight,
            mipLevel,
            padding,
            null
        );
    }

    /**
     * Produces the four-page physical layout used by the 32K virtual atlas.
     * One stable sprite is seeded onto each page, then all remaining entries
     * use the existing first-fit page packer.
     */
    public static <T extends Stitcher.Entry> Result<T>
        packVirtualAtlas(
            List<T> input,
            int mipLevel,
            int padding,
            List<Identifier> pageAnchors
        ) {
        if (pageAnchors.size()
            != PagesOfAtlasVirtualAtlas.PAGE_COUNT) {

            throw new IllegalArgumentException(
                "The virtual atlas requires exactly four page anchors"
            );
        }

        Result<T> baseline =
            pack(
                input,
                PagesOfAtlasVirtualAtlas.CELL_SIZE,
                PagesOfAtlasVirtualAtlas.CELL_SIZE,
                mipLevel,
                padding,
                null
            );

        if (
            baseline.pages().size()
                > PagesOfAtlasVirtualAtlas.PAGE_COUNT
        ) {
            throw new IllegalStateException(
                "The 32K virtual atlas supports exactly four cells, but baseline packing needs "
                    + baseline.pages().size()
                    + " pages"
            );
        }

        List<Holder<T>> holders =
            new ArrayList<>();

        for (T entry : input) {
            Holder<T> holder =
                createHolder(
                    entry,
                    mipLevel,
                    padding
                );

            holders.add(holder);
        }

        holders.sort(
            Comparator
                .<Holder<T>>comparingInt(h -> -h.height)
                .thenComparingInt(h -> -h.width)
                .thenComparing(h -> h.entry.name())
        );

        List<Page<T>> pages =
            new ArrayList<>(baseline.pages());

        while (
            pages.size()
                < PagesOfAtlasVirtualAtlas.PAGE_COUNT
        ) {
            pages.add(
                new Page<>(
                    pages.size(),
                    PagesOfAtlasVirtualAtlas.CELL_SIZE,
                    PagesOfAtlasVirtualAtlas.CELL_SIZE
                )
            );
        }

        java.util.Set<Holder<T>> anchors =
            java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>()
            );

        for (
            int page = 0;
            page < pageAnchors.size();
            page++
        ) {
            Identifier anchor =
                pageAnchors.get(page);

            Holder<T> holder =
                findVirtualPageAnchor(
                    holders,
                    pages.get(page),
                    anchor,
                    page,
                    anchors
                );

            Page<T> source =
                pages.stream()
                    .filter(candidate ->
                        candidate.contains(
                            holder.entry
                        )
                    )
                    .findFirst()
                    .orElseThrow();

            Page<T> target =
                pages.get(page);

            if (
                source != target
                && (
                    !source.remove(holder.entry)
                    || !target.add(holder, padding)
                )
            ) {
                throw new IllegalStateException(
                    "Virtual atlas cannot relocate anchor to page "
                        + page
                        + ": "
                        + holder.entry.name()
                );
            }

            target.promote(holder.entry);
            anchors.add(holder);
        }

        /*
         * TextureAtlas reserves one entry as the physical missing-sprite
         * alias. Give every page a second real entry so its sprite metadata
         * buffer is never empty after that alias is removed from its original
         * key.
         */
        for (
            int page = 0;
            page < pages.size();
            page++
        ) {
            Page<T> target = pages.get(page);

            while (target.placementCount() < 2) {
                boolean moved = false;

                for (
                    int candidateIndex = holders.size() - 1;
                    candidateIndex >= 0;
                    candidateIndex--
                ) {
                    Holder<T> candidate =
                        holders.get(candidateIndex);

                    if (anchors.contains(candidate)) {
                        continue;
                    }

                    Page<T> source =
                        pages.stream()
                            .filter(other ->
                                other != target
                                    && other.placementCount() > 2
                                    && other.contains(
                                        candidate.entry
                                    )
                            )
                            .findFirst()
                            .orElse(null);

                    if (
                        source != null
                        && target.add(candidate, padding)
                        && source.remove(candidate.entry)
                    ) {
                        moved = true;
                        break;
                    }
                }

                if (!moved) {
                    throw new IllegalStateException(
                        "Virtual atlas cannot place a second sprite on page "
                            + page
                    );
                }
            }
        }

        return new Result<>(
            List.copyOf(pages)
        );
    }

    private static <T extends Stitcher.Entry> Holder<T>
        findVirtualPageAnchor(
            List<Holder<T>> holders,
            Page<T> target,
            Identifier preferred,
            int page,
            java.util.Set<Holder<T>> used
        ) {
        String unwrapped =
            preferred.getPath().replace(
                "continuity_reserved/",
                ""
            );

        java.util.function.Predicate<Holder<T>> available =
            candidate -> !used.contains(candidate);

        return holders.stream()
            .filter(available)
            .filter(candidate ->
                candidate.entry.name().equals(preferred)
            )
            .findFirst()
            .or(() ->
                holders.stream()
                    .filter(available)
                    .filter(candidate ->
                        candidate.entry.name()
                            .getPath()
                            .endsWith(unwrapped)
                    )
                    .findFirst()
            )
            .or(() ->
                page <= 0
                    ? java.util.Optional.empty()
                    : holders.stream()
                        .filter(available)
                        .filter(candidate ->
                            target.contains(candidate.entry)
                                && candidate.entry.name()
                                    .getPath()
                                    .contains("cobblestone")
                        )
                        .findFirst()
            )
            .or(() ->
                page <= 0
                    ? java.util.Optional.empty()
                    : holders.stream()
                        .filter(available)
                        .filter(candidate ->
                            candidate.entry.name()
                                .getPath()
                                .contains("cobblestone")
                        )
                        .findFirst()
            )
            .or(() ->
                holders.stream()
                    .filter(available)
                    .filter(candidate ->
                        target.contains(candidate.entry)
                    )
                    .findFirst()
            )
            .or(() ->
                holders.stream()
                    .filter(available)
                    .findFirst()
            )
            .orElseThrow(() ->
                new IllegalStateException(
                    "Virtual-atlas anchor is missing: "
                        + preferred
                )
            );
    }

    /**
     * Packs an atlas while reserving one copy of {@code replicatedEntry}
     * on every physical page.
     *
     * The entry is identified by object identity and omitted from the
     * ordinary holder list. This is currently used only by the painting
     * atlas so every physical page contains its backing/edge sprite.
     */
    public static <T extends Stitcher.Entry> Result<T>
        packWithReplicatedEntry(
            List<T> input,
            int maxWidth,
            int maxHeight,
            int mipLevel,
            int padding,
            T replicatedEntry
        ) {
        if (replicatedEntry == null) {
            throw new IllegalArgumentException(
                "Replicated atlas entry cannot be null"
            );
        }

        return pack(
            input,
            maxWidth,
            maxHeight,
            mipLevel,
            padding,
            replicatedEntry
        );
    }

    private static <T extends Stitcher.Entry> Result<T> pack(
        List<T> input,
        int maxWidth,
        int maxHeight,
        int mipLevel,
        int padding,
        T replicatedEntry
    ) {
        List<Holder<T>> holders = new ArrayList<>();

        Holder<T> replicatedHolder =
            replicatedEntry == null
                ? null
                : createHolder(
                    replicatedEntry,
                    mipLevel,
                    padding
                );

        for (T entry : input) {
            if (entry == replicatedEntry) {
                continue;
            }

            holders.add(
                createHolder(
                    entry,
                    mipLevel,
                    padding
                )
            );
        }

        holders.sort(
            Comparator
                .<Holder<T>>comparingInt(h -> -h.height)
                .thenComparingInt(h -> -h.width)
                .thenComparing(h -> h.entry.name())
        );

        List<Page<T>> pages = new ArrayList<>();

        for (Holder<T> holder : holders) {
            boolean placed = false;

            for (Page<T> page : pages) {
                if (page.add(holder, padding)) {
                    placed = true;
                    break;
                }
            }

            if (!placed) {
                Page<T> page = new Page<>(
                    pages.size(),
                    maxWidth,
                    maxHeight
                );

                if (!page.add(holder, padding)) {
                    throw new IllegalStateException(
                        "Sprite cannot fit on an empty PagesOfAtlas page: "
                            + holder.entry.name()
                            + " ["
                            + holder.width
                            + "x"
                            + holder.height
                            + "]"
                    );
                }

                pages.add(page);
            }
        }

        if (replicatedHolder != null) {
            if (pages.isEmpty()) {
                pages.add(
                    new Page<>(
                        0,
                        maxWidth,
                        maxHeight
                    )
                );
            }

            /*
             * Replicate only after normal packing is complete. Adding a
             * tiny reserved sprite while pages are still growing can
             * change the packer's axis decisions and create otherwise
             * unnecessary full-size pages.
             */
            for (Page<T> page : pages) {
                if (!page.add(replicatedHolder, padding)) {
                    throw new IllegalStateException(
                        "Replicated sprite cannot fit on PagesOfAtlas page "
                            + page.number()
                            + ": "
                            + replicatedHolder.entry.name()
                            + " ["
                            + replicatedHolder.width
                            + "x"
                            + replicatedHolder.height
                            + "]"
                    );
                }
            }
        }

        return new Result<>(List.copyOf(pages));

    }

    private static <T extends Stitcher.Entry> Holder<T>
        createHolder(
            T entry,
            int mipLevel,
            int padding
        ) {
        return new Holder<>(
            entry,
            smallestFittingMinTexel(
                entry.width() + padding * 2,
                mipLevel
            ),
            smallestFittingMinTexel(
                entry.height() + padding * 2,
                mipLevel
            )
        );
    }

    private static int smallestFittingMinTexel(
        int input,
        int maxMipLevel
    ) {
        return ((input >> maxMipLevel)
            + (((input & ((1 << maxMipLevel) - 1)) == 0)
                ? 0
                : 1))
            << maxMipLevel;
    }

    private static final class Holder<T extends Stitcher.Entry> {
        final T entry;
        final int width;
        final int height;

        Holder(T entry, int width, int height) {
            this.entry = entry;
            this.width = width;
            this.height = height;
        }
    }

    public static final class Page<T extends Stitcher.Entry> {
        private final int number;
        private final int maxWidth;
        private final int maxHeight;

        private final List<Region<T>> storage =
            new ArrayList<>();

        private final List<Placement<T>> placements =
            new ArrayList<>();

        private int storageX;
        private int storageY;

        Page(int number, int maxWidth, int maxHeight) {
            this.number = number;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
        }

        boolean add(Holder<T> holder, int padding) {
            for (Region<T> region : storage) {
                Region<T> result = region.add(holder);

                if (result != null) {
                    placements.add(new Placement<>(
                        number,
                        holder.entry,
                        result.originX,
                        result.originY,
                        padding
                    ));

                    return true;
                }
            }

            Region<T> result = expand(holder);

            if (result != null) {
                placements.add(new Placement<>(
                    number,
                    holder.entry,
                    result.originX,
                    result.originY,
                    padding
                ));

                return true;
            }

            return false;
        }

        private Region<T> expand(Holder<T> holder) {
            int xCurrentSize =
                Mth.smallestEncompassingPowerOfTwo(storageX);

            int yCurrentSize =
                Mth.smallestEncompassingPowerOfTwo(storageY);

            int xNewSize =
                Mth.smallestEncompassingPowerOfTwo(
                    storageX + holder.width
                );

            int yNewSize =
                Mth.smallestEncompassingPowerOfTwo(
                    storageY + holder.height
                );

            boolean xCanGrow = xNewSize <= maxWidth;
            boolean yCanGrow = yNewSize <= maxHeight;

            if (!xCanGrow && !yCanGrow) {
                return null;
            }

            boolean xWillGrow =
                xCanGrow && xCurrentSize != xNewSize;

            boolean yWillGrow =
                yCanGrow && yCurrentSize != yNewSize;

            boolean growOnX;

            if (xWillGrow ^ yWillGrow) {
                growOnX = xWillGrow;
            } else {
                growOnX =
                    xCanGrow && xCurrentSize <= yCurrentSize;
            }

            Region<T> slot;

            if (growOnX) {
                if (storageY == 0) {
                    storageY = yNewSize;
                }

                slot = new Region<>(
                    storageX,
                    0,
                    xNewSize - storageX,
                    storageY
                );

                storageX = xNewSize;
            } else {
                slot = new Region<>(
                    0,
                    storageY,
                    storageX,
                    yNewSize - storageY
                );

                storageY = yNewSize;
            }

            Region<T> result = slot.add(holder);

            if (result == null) {
                return null;
            }

            storage.add(slot);
            return result;
        }

        public int number() {
            return number;
        }

        public int width() {
            return storageX;
        }

        public int height() {
            return storageY;
        }

        public List<Placement<T>> placements() {
            return List.copyOf(placements);
        }

        boolean contains(T entry) {
            return placements.stream()
                .anyMatch(
                    placement ->
                        placement.entry() == entry
                );
        }

        boolean remove(T entry) {
            return placements.removeIf(
                placement ->
                    placement.entry() == entry
            );
        }

        void promote(T entry) {
            for (
                int index = 0;
                index < placements.size();
                index++
            ) {
                Placement<T> placement =
                    placements.get(index);

                if (placement.entry() == entry) {
                    placements.remove(index);
                    placements.addFirst(placement);
                    return;
                }
            }
        }

        int placementCount() {
            return placements.size();
        }
    }

    private static final class Region<T extends Stitcher.Entry> {
        final int originX;
        final int originY;
        final int width;
        final int height;

        Holder<T> holder;
        List<Region<T>> subSlots;

        Region(
            int originX,
            int originY,
            int width,
            int height
        ) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
        }

        Region<T> add(Holder<T> holder) {
            if (this.holder != null) {
                return null;
            }

            if (holder.width > width ||
                holder.height > height) {
                return null;
            }

            if (holder.width == width &&
                holder.height == height) {

                this.holder = holder;
                return this;
            }

            if (subSlots == null) {
                subSlots = new ArrayList<>(3);

                subSlots.add(new Region<>(
                    originX,
                    originY,
                    holder.width,
                    holder.height
                ));

                int spareWidth = width - holder.width;
                int spareHeight = height - holder.height;

                if (spareHeight > 0 && spareWidth > 0) {
                    int right = Math.max(
                        height,
                        spareWidth
                    );

                    int bottom = Math.max(
                        width,
                        spareHeight
                    );

                    if (right >= bottom) {
                        subSlots.add(new Region<>(
                            originX,
                            originY + holder.height,
                            holder.width,
                            spareHeight
                        ));

                        subSlots.add(new Region<>(
                            originX + holder.width,
                            originY,
                            spareWidth,
                            height
                        ));
                    } else {
                        subSlots.add(new Region<>(
                            originX + holder.width,
                            originY,
                            spareWidth,
                            holder.height
                        ));

                        subSlots.add(new Region<>(
                            originX,
                            originY + holder.height,
                            width,
                            spareHeight
                        ));
                    }
                } else if (spareWidth == 0) {
                    subSlots.add(new Region<>(
                        originX,
                        originY + holder.height,
                        holder.width,
                        spareHeight
                    ));
                } else if (spareHeight == 0) {
                    subSlots.add(new Region<>(
                        originX + holder.width,
                        originY,
                        spareWidth,
                        holder.height
                    ));
                }
            }

            for (Region<T> sub : subSlots) {
                Region<T> result = sub.add(holder);

                if (result != null) {
                    return result;
                }
            }

            return null;
        }
    }

    public record Placement<T extends Stitcher.Entry>(
        int page,
        T entry,
        int x,
        int y,
        int padding
    ) {
        public Identifier name() {
            return entry.name();
        }
    }

    public record Result<T extends Stitcher.Entry>(
        List<Page<T>> pages
    ) {}
}
