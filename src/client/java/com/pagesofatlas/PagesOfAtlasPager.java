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
        List<Holder<T>> holders = new ArrayList<>();

        for (T entry : input) {
            holders.add(new Holder<>(
                entry,
                smallestFittingMinTexel(
                    entry.width() + padding * 2,
                    mipLevel
                ),
                smallestFittingMinTexel(
                    entry.height() + padding * 2,
                    mipLevel
                )
            ));
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

        return new Result<>(List.copyOf(pages));

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
