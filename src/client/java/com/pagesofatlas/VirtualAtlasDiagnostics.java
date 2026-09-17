package com.pagesofatlas;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/** Final-boundary invariants for the experimental virtual UV stream. */
public final class VirtualAtlasDiagnostics {

    private VirtualAtlasDiagnostics() {}

    private static final double TOLERANCE_PIXELS = 2.0;

    private static final boolean PROFILING =
        Boolean.getBoolean(
            "pagesofatlas.virtualAtlasDiagnosticsProfile"
        )
        || Files.isRegularFile(
            Path.of(
                "mods",
                "pagesofatlas-virtual-atlas-diagnostics-profile.enable"
            )
        );

    private static final boolean ENABLED =
        PROFILING
        || Boolean.getBoolean(
            "pagesofatlas.virtualAtlasDiagnostics"
        )
        || Files.isRegularFile(
            Path.of(
                "mods",
                "pagesofatlas-virtual-atlas-diagnostics.enable"
            )
        );

    private static final AtomicInteger PROFILE_REPORTS =
        new AtomicInteger();

    private static final ThreadLocal<ProfileStats> PROFILE_STATS =
        ThreadLocal.withInitial(ProfileStats::new);

    private static final Set<String> REPORTED =
        ConcurrentHashMap.newKeySet();

    private static final AtomicInteger CLEAN_PAGE_MASK =
        new AtomicInteger();

    private static final Map<Class<?>, SodiumUvMethods>
        SODIUM_METHODS =
            new ConcurrentHashMap<>();

    public static boolean enabled() {
        return PagesOfAtlasVirtualAtlas.enabled()
            && ENABLED;
    }

    public static void inspect(
        String context,
        QuadView quad
    ) {
        float[] u = new float[4];
        float[] v = new float[4];

        for (int vertex = 0; vertex < 4; vertex++) {
            u[vertex] = quad.u(vertex);
            v[vertex] = quad.v(vertex);
        }

        inspect(context, u, v);
    }

    public static void inspectSodium(
        String context,
        Object quad
    ) {
        long started =
            PROFILING
                ? System.nanoTime()
                : 0L;

        try {
            SodiumUvMethods methods =
                SODIUM_METHODS.computeIfAbsent(
                    quad.getClass(),
                    VirtualAtlasDiagnostics::findSodiumMethods
                );

            float[] u = new float[4];
            float[] v = new float[4];

            for (int vertex = 0; vertex < 4; vertex++) {
                u[vertex] =
                    ((Number)methods.u().invoke(
                        quad,
                        vertex
                    )).floatValue();

                v[vertex] =
                    ((Number)methods.v().invoke(
                        quad,
                        vertex
                    )).floatValue();
            }

            inspect(context, u, v);
        } catch (Throwable t) {
            if (REPORTED.add("sodium-reflection")) {
                PagesOfAtlasClient.LOGGER.error(
                    "[VIRTUAL ATLAS DIAGNOSTIC] Could not inspect Sodium's final quad UVs",
                    t
                );
            }
        } finally {
            if (PROFILING) {
                recordProfile(
                    System.nanoTime() - started
                );
            }
        }
    }

    private static void recordProfile(long elapsedNanos) {
        ProfileStats stats =
            PROFILE_STATS.get();

        stats.calls++;
        stats.nanos += elapsedNanos;

        if (stats.calls < 2_048) {
            return;
        }

        int report =
            PROFILE_REPORTS.getAndIncrement();

        if (report < 8) {
            PagesOfAtlasClient.LOGGER.warn(
                "[VIRTUAL ATLAS PROFILE] Diagnostics processed {} Sodium quads in {} ms ({} us/quad) on {}",
                stats.calls,
                stats.nanos / 1_000_000.0,
                stats.nanos / (stats.calls * 1_000.0),
                Thread.currentThread().getName()
            );
        }

        stats.calls = 0;
        stats.nanos = 0L;
    }

    private static SodiumUvMethods findSodiumMethods(
        Class<?> type
    ) {
        try {
            Method u =
                type.getMethod(
                    "getTexU",
                    int.class
                );

            Method v =
                type.getMethod(
                    "getTexV",
                    int.class
                );

            return new SodiumUvMethods(u, v);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "Unsupported Sodium quad type "
                    + type.getName(),
                exception
            );
        }
    }

    private static void inspect(
        String context,
        float[] u,
        float[] v
    ) {
        if (!PagesOfAtlasVirtualAtlas.enabled()) {
            return;
        }

        Optional<PagesOfAtlasRegistry.AtlasPlan> planOptional =
            PagesOfAtlasRegistry.plan(
                TextureAtlas.LOCATION_BLOCKS
            );

        if (planOptional.isEmpty()) {
            return;
        }

        int[] pages = new int[4];
        double[] localX = new double[4];
        double[] localY = new double[4];

        for (int vertex = 0; vertex < 4; vertex++) {
            double virtualX =
                u[vertex]
                    * PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE;

            double virtualY =
                v[vertex]
                    * PagesOfAtlasVirtualAtlas.VIRTUAL_SIZE;

            int pageX =
                (int)Math.floor(
                    virtualX
                        / PagesOfAtlasVirtualAtlas.CELL_SIZE
                );

            int pageY =
                (int)Math.floor(
                    virtualY
                        / PagesOfAtlasVirtualAtlas.CELL_SIZE
                );

            pages[vertex] =
                pageY * 2
                    + pageX;

            localX[vertex] =
                virtualX
                    - pageX
                        * PagesOfAtlasVirtualAtlas.CELL_SIZE;

            localY[vertex] =
                virtualY
                    - pageY
                        * PagesOfAtlasVirtualAtlas.CELL_SIZE;
        }

        boolean mixed =
            pages[1] != pages[0]
                || pages[2] != pages[0]
                || pages[3] != pages[0];

        if (mixed) {
            Identifier sprite =
                findSprite(
                    pages[0],
                    localX,
                    localY
                );

            String signature =
                "mixed:"
                    + context
                    + Arrays.toString(pages)
                    + sprite;

            if (REPORTED.add(signature)) {
                PagesOfAtlasClient.LOGGER.error(
                    "[VIRTUAL ATLAS DIAGNOSTIC] ARCHITECTURAL ERROR mixed-page quad; context={} sprite={} pages={} virtualU={} virtualV={} localX={} localY={}",
                    context,
                    sprite,
                    Arrays.toString(pages),
                    Arrays.toString(u),
                    Arrays.toString(v),
                    Arrays.toString(localX),
                    Arrays.toString(localY)
                );
            }

            return;
        }

        int page = pages[0];

        Optional<PagesOfAtlasRegistry.PagePlan> pagePlan =
            planOptional.get().page(page);

        boolean outOfRange =
            pagePlan.isEmpty();

        if (pagePlan.isPresent()) {
            double maxX =
                pagePlan.get().width()
                    + TOLERANCE_PIXELS;

            double maxY =
                pagePlan.get().height()
                    + TOLERANCE_PIXELS;

            for (int vertex = 0; vertex < 4; vertex++) {
                if (
                    localX[vertex] < -TOLERANCE_PIXELS
                    || localY[vertex] < -TOLERANCE_PIXELS
                    || localX[vertex] > maxX
                    || localY[vertex] > maxY
                ) {
                    outOfRange = true;
                }
            }
        }

        if (outOfRange) {
            Identifier sprite =
                findSprite(
                    page,
                    localX,
                    localY
                );

            String signature =
                "range:"
                    + context
                    + page
                    + sprite;

            if (REPORTED.add(signature)) {
                PagesOfAtlasClient.LOGGER.error(
                    "[VIRTUAL ATLAS DIAGNOSTIC] Page-local UV outside uploaded texture; context={} sprite={} page={} physicalSize={}x{} virtualU={} virtualV={} localX={} localY={} tolerance={}px",
                    context,
                    sprite,
                    page,
                    pagePlan.map(
                        PagesOfAtlasRegistry.PagePlan::width
                    ).orElse(-1),
                    pagePlan.map(
                        PagesOfAtlasRegistry.PagePlan::height
                    ).orElse(-1),
                    Arrays.toString(u),
                    Arrays.toString(v),
                    Arrays.toString(localX),
                    Arrays.toString(localY),
                    TOLERANCE_PIXELS
                );
            }

            return;
        }

        if (markCleanPage(page)) {
            Identifier sprite =
                findSprite(
                    page,
                    localX,
                    localY
                );

            PagesOfAtlasClient.LOGGER.info(
                "[VIRTUAL ATLAS DIAGNOSTIC] Clean final quad; context={} sprite={} page={} virtualU={} virtualV={} localX={} localY={} physicalSize={}x{}",
                context,
                sprite,
                page,
                Arrays.toString(u),
                Arrays.toString(v),
                Arrays.toString(localX),
                Arrays.toString(localY),
                pagePlan.get().width(),
                pagePlan.get().height()
            );
        }
    }

    private static boolean markCleanPage(int page) {
        if (
            page < 0
            || page >= PagesOfAtlasVirtualAtlas.PAGE_COUNT
        ) {
            return false;
        }

        int bit =
            1 << page;

        int current =
            CLEAN_PAGE_MASK.get();

        while ((current & bit) == 0) {
            if (
                CLEAN_PAGE_MASK.compareAndSet(
                    current,
                    current | bit
                )
            ) {
                return true;
            }

            current =
                CLEAN_PAGE_MASK.get();
        }

        return false;
    }

    private static Identifier findSprite(
        int page,
        double[] localX,
        double[] localY
    ) {
        if (
            page < 0
            || page >= PagesOfAtlasVirtualAtlas.PAGE_COUNT
        ) {
            return null;
        }

        double centerX =
            (localX[0]
                + localX[1]
                + localX[2]
                + localX[3])
                * 0.25;

        double centerY =
            (localY[0]
                + localY[1]
                + localY[2]
                + localY[3])
                * 0.25;

        return PagesOfAtlasRegistry.spriteAtPhysicalPixel(
            TextureAtlas.LOCATION_BLOCKS,
            page,
            centerX,
            centerY,
            TOLERANCE_PIXELS
        ).orElse(null);
    }

    private record SodiumUvMethods(
        Method u,
        Method v
    ) {}

    private static final class ProfileStats {
        private int calls;
        private long nanos;
    }
}
