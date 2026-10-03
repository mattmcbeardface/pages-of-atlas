package com.pagesofatlas.compat;

public final class SodiumExtendedBlockModelPageContext {

    private static final int NO_PAGE = -1;

    private static final ThreadLocal<int[]> CURRENT_PAGE =
        ThreadLocal.withInitial(
            () -> new int[] { NO_PAGE }
        );

    private SodiumExtendedBlockModelPageContext() {}

    public static void set(int page) {
        CURRENT_PAGE.get()[0] = page;
    }

    public static int get() {
        return CURRENT_PAGE.get()[0];
    }

    public static void clear() {
        CURRENT_PAGE.get()[0] = NO_PAGE;
    }
}
