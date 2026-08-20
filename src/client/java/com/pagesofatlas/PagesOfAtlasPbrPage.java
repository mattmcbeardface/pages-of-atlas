package com.pagesofatlas;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * GPU-side PBR companions for one physical PagesOfAtlas page.
 *
 * The important invariant is:
 *
 *     diffuse(page, x, y)
 *     normal (page, x, y)
 *     specular(page, x, y)
 *
 * always describe the same logical sprite.
 *
 * There is no independent PBR stitch operation.
 */
public final class PagesOfAtlasPbrPage
    implements AutoCloseable {

    /*
     * EXPERIMENT:
     *
     * Keep the logical POA atlas dimensions separate from the
     * physical PBR texture dimensions.
     *
     * PBR textures currently use the same physical resolution
     * as the corresponding diffuse POA page.
     *
     *     16384x16384 logical page
     *              ->
     *      16384x16384 PBR page
     */
    public static final int PBR_RESOLUTION_DIVISOR = 2;

    private final int page;

    private final int logicalWidth;
    private final int logicalHeight;

    private final int width;
    private final int height;

    private GpuTexture normalTexture;
    private GpuTextureView normalView;

    private GpuTexture specularTexture;
    private GpuTextureView specularView;

    public PagesOfAtlasPbrPage(
        int page,
        int width,
        int height
    ) {
        this.page = page;

        this.logicalWidth = width;
        this.logicalHeight = height;

        this.width =
            Math.max(
                1,
                width / PBR_RESOLUTION_DIVISOR
            );

        this.height =
            Math.max(
                1,
                height / PBR_RESOLUTION_DIVISOR
            );
    }

    public void allocate() {
        close();

        GpuDevice device =
            RenderSystem.getDevice();

        /*
         * Start deliberately with ONE mip level.
         *
         * That lets us validate:
         *
         *   placement
         *   PBR routing
         *   memory behavior
         *
         * before introducing PBR-aware mip generation.
         *
         * Usage 15 matches Minecraft/Iris atlas textures:
         *
         * COPY_DST | COPY_SRC |
         * TEXTURE_BINDING | RENDER_ATTACHMENT
         */
        this.normalTexture =
            device.createTexture(
                () ->
                    "PagesOfAtlas normal page "
                        + this.page,
                15,
                GpuFormat.RGBA8_UNORM,
                this.width,
                this.height,
                1,
                1
            );

        this.normalView =
            device.createTextureView(
                this.normalTexture
            );

        this.specularTexture =
            device.createTexture(
                () ->
                    "PagesOfAtlas specular page "
                        + this.page,
                15,
                GpuFormat.RGBA8_UNORM,
                this.width,
                this.height,
                1,
                1
            );

        this.specularView =
            device.createTextureView(
                this.specularTexture
            );

        PagesOfAtlasClient.LOGGER.info(
            "[PBR PAGE] Allocated page {} PBR textures {}x{} from logical {}x{} (1/{})",
            this.page,
            this.width,
            this.height,
            this.logicalWidth,
            this.logicalHeight,
            PBR_RESOLUTION_DIVISOR
        );
    }

    public int page() {
        return this.page;
    }

    public int logicalWidth() {
        return this.logicalWidth;
    }

    public int logicalHeight() {
        return this.logicalHeight;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public GpuTexture normalTexture() {
        return this.normalTexture;
    }

    public GpuTextureView normalView() {
        return this.normalView;
    }

    public GpuTexture specularTexture() {
        return this.specularTexture;
    }

    public GpuTextureView specularView() {
        return this.specularView;
    }

    public boolean allocated() {
        return
            this.normalTexture != null
            && this.specularTexture != null;
    }

    @Override
    public void close() {
        if (this.normalView != null) {
            this.normalView.close();
            this.normalView = null;
        }

        if (this.normalTexture != null) {
            this.normalTexture.close();
            this.normalTexture = null;
        }

        if (this.specularView != null) {
            this.specularView.close();
            this.specularView = null;
        }

        if (this.specularTexture != null) {
            this.specularTexture.close();
            this.specularTexture = null;
        }
    }
}
