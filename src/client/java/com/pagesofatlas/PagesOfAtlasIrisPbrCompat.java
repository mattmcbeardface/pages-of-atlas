package com.pagesofatlas;

/**
 * Iris PBR lifecycle compatibility.
 *
 * Iris retains its native normal/specular texture holders across
 * shader pipeline changes. When a newly constructed pipeline no
 * longer requires PBR, POA explicitly clears Iris's PBR texture
 * manager so the old blocks_n / blocks_s atlases are released.
 *
 * Reflection keeps Iris optional and avoids a hard runtime
 * dependency on its internal implementation classes.
 */
public final class PagesOfAtlasIrisPbrCompat {

    private PagesOfAtlasIrisPbrCompat() {}

    public static void clear() {
        try {
            Class<?> managerClass =
                Class.forName(
                    "net.irisshaders.iris.pbr.texture.PBRTextureManager"
                );

            Object manager =
                managerClass
                    .getField("INSTANCE")
                    .get(null);

            managerClass
                .getMethod("clear")
                .invoke(manager);

            PagesOfAtlasClient.LOGGER.info(
                "[PBR PURGE] Cleared Iris PBR texture manager"
            );

        } catch (
            ReflectiveOperationException
            | LinkageError ignored
        ) {
            /*
             * Iris is optional. If it is absent or its internal API
             * changes, leave Minecraft's normal texture lifecycle
             * untouched.
             */
        }
    }
}
