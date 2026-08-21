package com.pagesofatlas.mixin;

import com.pagesofatlas.PagesOfAtlasRegistry;

import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pages of Atlas compatibility for Iris + Sodium terrain shaders.
 *
 * The physical atlas page travels through:
 *
 *   Fabric quad tag
 *       ->
 *   Sodium material bits 3-4
 *       ->
 *   Iris XHFP a_LightAndData.z bits 3-4
 *
 * This patch:
 *
 *   1. recovers the page number in the terrain vertex shader,
 *   2. passes it flat to the fragment shader,
 *   3. detects the shader pack's actual diffuse terrain sampler,
 *   4. routes diffuse texture() and textureLod() calls to the
 *      appropriate Pages of Atlas physical texture page.
 *
 * No shader-pack-specific sampler name is assumed.
 */
@Pseudo
@Mixin(
    targets =
        "net.irisshaders.iris.pipeline.transform.TransformPatcher",
    remap = false
)
public abstract class IrisTransformPatcherMixin {

    private static final String[] DIFFUSE_SAMPLER_CANDIDATES = {
        "tex",
        "gtexture",
        "texture",
        "textureSampler",
        "Sampler0",
        "u_Texture",
        "DiffuseSampler",
        "gtexture",
        "texture",
        "u_MainSampler"
    };

    private static final Pattern SAMPLER_2D_PATTERN =
        Pattern.compile(
            "\\buniform\\s+sampler2D\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
        );

    @Inject(
        method = "patchSodium",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private static void pagesofatlas$routePhysicalAtlasPage(
        String name,
        String vertex,
        String geometry,
        String tessControl,
        String tessEval,
        String fragment,
        @Coerce Object alphaTest,
        @Coerce Object textureMap,
        boolean shadow,
        CallbackInfoReturnable<Map<?, String>> cir
    ) {
        Map<?, String> original =
            cir.getReturnValue();

        if (original == null || original.isEmpty()) {
            return;
        }

        Map<Object, String> patched =
            new HashMap<>();

        for (Map.Entry<?, String> entry : original.entrySet()) {
            Object key =
                entry.getKey();

            String source =
                entry.getValue();

            if (source == null) {
                patched.put(
                    key,
                    null
                );

                continue;
            }

            String stage =
                String.valueOf(key)
                    .toUpperCase();

            if (stage.contains("VERTEX")) {
                source =
                    pagesofatlas$patchVertex(
                        source
                    );
            }

            if (stage.contains("FRAGMENT")) {
                source =
                    pagesofatlas$patchFragment(
                        source
                    );
            }

            patched.put(
                key,
                source
            );
        }

        cir.setReturnValue(
            patched
        );
    }

    private static String pagesofatlas$patchVertex(
        String source
    ) {
        if (!source.contains(
                "flat out uint pagesofatlas_page;")) {

            int main =
                source.indexOf(
                    "void main()"
                );

            if (main >= 0) {
                source =
                    source.substring(
                        0,
                        main
                    )
                    +
                    "flat out uint pagesofatlas_page;\n"
                    +
                    source.substring(
                        main
                    );
            }
        }

        String mainNeedle =
            "void main() {";

        String assignment =
            "pagesofatlas_page = "
            + "(a_LightAndData.z >> 3u) & 3u;";

        if (
            source.contains(mainNeedle)
            &&
            !source.contains(assignment)
        ) {
            source =
                source.replace(
                    mainNeedle,
                    mainNeedle
                    + "\n    "
                    + assignment
                );
        }

        return source;
    }

    private static String pagesofatlas$patchFragment(
        String source
    ) {
        String diffuseSampler =
            pagesofatlas$findDiffuseSampler(
                source
            );

        /*
         * Use the same authoritative mip level that POA used when
         * constructing the physical block-atlas pages.
         *
         * Do not ask GLSL to discover the mip count; some shader
         * profiles used by Iris do not expose textureQueryLevels().
         *
         * If transformation somehow occurs before the upload bundle
         * exists, fall back conservatively to mip 0.
         */
        int maxMipLevel =
            PagesOfAtlasRegistry
                .uploadBundle(
                    TextureAtlas.LOCATION_BLOCKS
                )
                .map(
                    bundle ->
                        bundle.combined()
                            .mipLevel()
                )
                .orElse(0);

        maxMipLevel =
            Math.max(
                maxMipLevel,
                0
            );

        String maxMipLiteral =
            Integer.toString(
                maxMipLevel
            )
            + ".0";

        /*
         * If this transformed program does not expose a known
         * terrain diffuse sampler, leave it alone.
         *
         * This prevents Pages of Atlas from injecting helpers
         * into unrelated shader passes.
         */
        if (diffuseSampler == null) {
            return source;
        }

        /*
         * Rewrite only calls which actually use this program's
         * detected terrain diffuse sampler.
         *
         * The helper functions are injected afterward so these
         * replacements cannot recurse into their own bodies.
         */
        source =
            pagesofatlas$replaceTextureCalls(
                source,
                diffuseSampler
            );

        /*
         * Some shader packs pass the terrain atlas through a local
         * sampler parameter before sampling it.
         *
         * Complementary's anisotropic filtering path is a canonical
         * example:
         *
         *   textureAF(tex, uv)
         *       ->
         *   textureLod(texSampler, sampleUV, lod)
         *
         * Direct diffuse rewriting cannot see that texSampler aliases
         * the terrain atlas. When that exact terrain-filter pattern is
         * present, route the alias through POA as well.
         */
        source =
            pagesofatlas$routeDiffuseSamplerAliases(
                source,
                diffuseSampler
            );

        /*
         * Bliss-style POM helper routing.
         *
         * Bliss passes gtexture, normals, and specular through the
         * same sampler2D helper:
         *
         *   texture2D_POMSwitch(gtexture, ...)
         *   texture2D_POMSwitch(normals, ...)
         *   texture2D_POMSwitch(specular, ...)
         *
         * The helper's sampler parameter therefore cannot be
         * rewritten globally. Rewrite only the invocation whose
         * first argument is the detected diffuse terrain sampler.
         */
        source =
            pagesofatlas$routePomSwitchDiffuse(
                source,
                diffuseSampler
            );

        /*
         * Route conventional Iris / OptiFine / labPBR terrain
         * normal and specular samplers through POA's physical
         * companion pages.
         */
        source =
            pagesofatlas$replacePbrTextureCalls(
                source
            );

        source =
            pagesofatlas$ensureFragmentGlobals(
                source
            );

        source =
            pagesofatlas$insertPbrHelpersEarly(
                source
            );

        /*
         * textureGrad() is commonly used from PBR/parallax utility
         * functions which occur before main(). Put only this helper
         * ahead of the first function that actually calls it.
         */
        source =
            pagesofatlas$insertTextureGradHelperEarly(
                source,
                diffuseSampler
            );

        Matcher mainMatcher =
            Pattern.compile(
                "\\bvoid\\s+main\\s*\\("
            ).matcher(source);

        if (!mainMatcher.find()) {
            return source;
        }

        int main =
            mainMatcher.start();

        StringBuilder injection =
            new StringBuilder();


        if (!source.contains(
                "flat in uint pagesofatlas_page;")) {

            injection.append(
                "flat in uint pagesofatlas_page;\n"
            );
        }

        if (!source.contains(
                "uniform sampler2D u_BlockTex1;")) {

            injection.append(
                "uniform sampler2D u_BlockTex1;\n"
            );
        }

        if (!source.contains(
                "uniform sampler2D u_BlockTex2;")) {

            injection.append(
                "uniform sampler2D u_BlockTex2;\n"
            );
        }

        if (!source.contains(
                "uniform sampler2D u_BlockTex3;")) {

            injection.append(
                "uniform sampler2D u_BlockTex3;\n"
            );
        }

        if (!source.contains(
                "vec4 pagesofatlas_texture(vec2 uv) {")) {

            injection.append(
                "\n"
                + "vec4 pagesofatlas_texture(vec2 uv) {\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return textureLod(u_BlockTex1, uv, 0.5);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return textureLod(u_BlockTex2, uv, 0.5);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return textureLod(u_BlockTex3, uv, 0.5);\n"
                + "    }\n"
                + "    return textureLod("
                + diffuseSampler
                + ", uv, 0.5);\n"
                + "}\n\n"
            );
        }

        if (!source.contains(
                "vec4 pagesofatlas_texture(vec2 uv, float bias) {")) {

            injection.append(
                "vec4 pagesofatlas_texture(vec2 uv, float bias) {\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return textureLod(u_BlockTex1, uv, 0.0);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return textureLod(u_BlockTex2, uv, 0.0);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return textureLod(u_BlockTex3, uv, 0.0);\n"
                + "    }\n"
                + "    return textureLod("
                + diffuseSampler
                + ", uv, 0.0);\n"
                + "}\n\n"
            );
        }


        if (!source.contains(
                "vec4 pagesofatlas_textureGrad(vec2 uv, vec2 dx, vec2 dy) {")) {

            injection.append(
                "vec4 pagesofatlas_textureGrad(vec2 uv, vec2 dx, vec2 dy) {\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return textureGrad(u_BlockTex1, uv, dx, dy);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return textureGrad(u_BlockTex2, uv, dx, dy);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return textureGrad(u_BlockTex3, uv, dx, dy);\n"
                + "    }\n"
                + "    return textureGrad("
                + diffuseSampler
                + ", uv, dx, dy);\n"
                + "}\n\n"
            );
        }

        if (!source.contains(
                "vec4 pagesofatlas_textureLod(vec2 uv, float lod) {")) {

            injection.append(
                "vec4 pagesofatlas_textureLod(vec2 uv, float lod) {\n"
                + "    float pagesofatlas_explicit_lod = clamp(lod, 0.0, "
                + maxMipLiteral
                + ");\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return textureLod(u_BlockTex1, uv, pagesofatlas_explicit_lod);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return textureLod(u_BlockTex2, uv, pagesofatlas_explicit_lod);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return textureLod(u_BlockTex3, uv, pagesofatlas_explicit_lod);\n"
                + "    }\n"
                + "    return textureLod("
                + diffuseSampler
                + ", uv, pagesofatlas_explicit_lod);\n"
                + "}\n\n"
            );
        }

        if (!injection.isEmpty()) {
            source =
                source.substring(0, main)
                + injection
                + source.substring(main);
        }

        return source;
    }

    private static String pagesofatlas$findDiffuseSampler(
        String source
    ) {
        /*
         * Prefer the canonical aliases Iris commonly uses for
         * terrain diffuse sampling.
         */
        for (
            String candidate :
            DIFFUSE_SAMPLER_CANDIDATES
        ) {
            /*
             * GLSL permits multiple sampler declarations in one
             * statement, for example:
             *
             *     uniform sampler2D tex, noisetex;
             *
             * Several shader packs, including Solas, use this form.
             *
             * Match the complete declaration and then test each
             * comma-separated identifier rather than requiring the
             * diffuse sampler to appear alone.
             */
            Pattern declaration =
                Pattern.compile(
                    "\\buniform\\s+sampler2D\\s+"
                    + "([^;]+);"
                );

            Matcher declarationMatcher =
                declaration.matcher(source);

            while (declarationMatcher.find()) {
                String[] names =
                    declarationMatcher
                        .group(1)
                        .split(",");

                for (String rawName : names) {
                    String name =
                        rawName.trim();

                    /*
                     * Be conservative. Ignore anything that is not
                     * a plain GLSL identifier.
                     */
                    if (!name.matches(
                            "[A-Za-z_][A-Za-z0-9_]*")) {
                        continue;
                    }

                    if (name.equals(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        /*
         * Do not guess from arbitrary sampler2D declarations.
         *
         * A terrain shader may also contain colortex, shadow,
         * noise, normal and specular samplers. Routing those as
         * page-zero would corrupt unrelated shader inputs.
         */
        Matcher matcher =
            SAMPLER_2D_PATTERN.matcher(
                source
            );

        while (matcher.find()) {
            String name =
                matcher.group(1);

            if (
                name.equals("u_BlockTex1")
                ||
                name.equals("u_BlockTex2")
                ||
                name.equals("u_BlockTex3")
            ) {
                continue;
            }
        }

        return null;
    }

    private static String pagesofatlas$ensureFragmentGlobals(
        String source
    ) {
        /*
         * Find the first real GLSL function definition.
         *
         * Everything before this point is shader-global territory.
         */
        Pattern functionPattern =
            Pattern.compile(
                "(?m)^[ \\t]*"
                + "(?:[A-Za-z_][A-Za-z0-9_]*[ \\t]+)+"
                + "[A-Za-z_][A-Za-z0-9_]*[ \\t]*"
                + "\\([^;{}]*\\)[ \\t]*\\{"
            );

        Matcher matcher =
            functionPattern.matcher(
                source
            );

        if (!matcher.find()) {
            return source;
        }

        int insertAt =
            matcher.start();

        StringBuilder globals =
            new StringBuilder();

        /*
         * Every declaration is independently idempotent.
         */
        if (!source.contains(
                "flat in uint pagesofatlas_page;")) {

            globals.append(
                "flat in uint pagesofatlas_page;\n"
            );
        }

        if (!source.contains(
                "vec4 pagesofatlas_texture(vec2 uv);")) {

            globals.append(
                "vec4 pagesofatlas_texture(vec2 uv);\n"
            );
        }

        if (!source.contains(
                "vec4 pagesofatlas_texture(vec2 uv, float bias);")) {

            globals.append(
                "vec4 pagesofatlas_texture(vec2 uv, float bias);\n"
            );
        }

        if (!source.contains(
                "vec4 pagesofatlas_textureGrad(vec2 uv, vec2 dx, vec2 dy);")) {

            globals.append(
                "vec4 pagesofatlas_textureGrad(vec2 uv, vec2 dx, vec2 dy);\n"
            );
        }

        if (!source.contains(
                "vec4 pagesofatlas_textureLod(vec2 uv, float lod);")) {

            globals.append(
                "vec4 pagesofatlas_textureLod(vec2 uv, float lod);\n"
            );
        }

        if (globals.isEmpty()) {
            return source;
        }

        return source.substring(
                0,
                insertAt
            )
            + globals
            + source.substring(
                insertAt
            );
    }

    private static String pagesofatlas$insertTextureGradHelperEarly(
        String source,
        String diffuseSampler
    ) {
        String call =
            "pagesofatlas_textureGrad(";

        int callIndex =
            source.indexOf(call);

        if (callIndex < 0) {
            return source;
        }

        /*
         * Find the last real GLSL function definition before the
         * first routed textureGrad call. This identifies the utility
         * function containing the call without moving declarations
         * ahead of #version, structs, uniforms, etc.
         */
        Pattern functionPattern =
            Pattern.compile(
                "(?m)^[ \\t]*"
                + "(?:[A-Za-z_][A-Za-z0-9_]*[ \\t]+)+"
                + "[A-Za-z_][A-Za-z0-9_]*[ \\t]*"
                + "\\([^;{}]*\\)[ \\t]*\\{"
            );

        Matcher matcher =
            functionPattern.matcher(source);

        int insertAt =
            -1;

        while (
            matcher.find()
            && matcher.start() < callIndex
        ) {
            insertAt =
                matcher.start();
        }

        if (insertAt < 0) {
            /*
             * Better to leave the shader unchanged than inject into
             * an unknown location.
             */
            return source;
        }

        StringBuilder early =
            new StringBuilder();

        /*
         * pagesofatlas_page is a shader-global declaration.
         *
         * Another POA helper may already have injected it at a
         * different position in the shader. Checking only the text
         * before this helper's insertion point can therefore create
         * a second global declaration.
         */
        if (!source.contains(
                "flat in uint pagesofatlas_page;")) {

            early.append(
                "flat in uint pagesofatlas_page;\n"
            );
        }

        if (!source.substring(0, insertAt).contains(
                "uniform sampler2D u_BlockTex1;")) {

            early.append(
                "uniform sampler2D u_BlockTex1;\n"
            );
        }

        if (!source.substring(0, insertAt).contains(
                "uniform sampler2D u_BlockTex2;")) {

            early.append(
                "uniform sampler2D u_BlockTex2;\n"
            );
        }

        if (!source.substring(0, insertAt).contains(
                "uniform sampler2D u_BlockTex3;")) {

            early.append(
                "uniform sampler2D u_BlockTex3;\n"
            );
        }

        early.append(
            "\n"
            + "vec4 pagesofatlas_textureGrad(vec2 uv, vec2 dx, vec2 dy) {\n"
            + "    if (pagesofatlas_page == 1u) {\n"
            + "        return textureGrad(u_BlockTex1, uv, dx, dy);\n"
            + "    }\n"
            + "    if (pagesofatlas_page == 2u) {\n"
            + "        return textureGrad(u_BlockTex2, uv, dx, dy);\n"
            + "    }\n"
            + "    if (pagesofatlas_page == 3u) {\n"
            + "        return textureGrad(u_BlockTex3, uv, dx, dy);\n"
            + "    }\n"
            + "    return textureGrad("
            + diffuseSampler
            + ", uv, dx, dy);\n"
            + "}\n\n"
        );

        return source.substring(0, insertAt)
            + early
            + source.substring(insertAt);
    }

    private static String pagesofatlas$routeDiffuseSamplerAliases(
        String source,
        String diffuseSampler
    ) {
        /*
         * Do not rewrite arbitrary sampler parameters.
         *
         * Require both:
         *
         *   1. a textureAF(sampler2D texSampler, ...) definition
         *   2. a textureAF(<detected diffuse sampler>, ...) call
         *
         * Together these establish the alias relationship used by
         * Complementary-style manual anisotropic filtering.
         */
        Pattern definition =
            Pattern.compile(
                "\\btextureAF\\s*\\(\\s*sampler2D\\s+"
                + "([A-Za-z_][A-Za-z0-9_]*)\\s*,"
            );

        Matcher matcher =
            definition.matcher(
                source
            );

        if (!matcher.find()) {
            return source;
        }

        String alias =
            matcher.group(1);

        Pattern diffuseCall =
            Pattern.compile(
                "\\btextureAF\\s*\\(\\s*"
                + Pattern.quote(diffuseSampler)
                + "\\s*,"
            );

        if (!diffuseCall.matcher(source).find()) {
            return source;
        }

        String aliasPattern =
            Pattern.quote(alias);

        source =
            source.replaceAll(
                "\\btextureLod\\s*\\(\\s*"
                + aliasPattern
                + "\\s*,",
                "pagesofatlas_textureLod("
            );

        source =
            source.replaceAll(
                "\\btextureGrad\\s*\\(\\s*"
                + aliasPattern
                + "\\s*,",
                "pagesofatlas_textureGrad("
            );

        source =
            source.replaceAll(
                "\\btexture\\s*\\(\\s*"
                + aliasPattern
                + "\\s*,",
                "pagesofatlas_texture("
            );

        return source;
    }

    private static String pagesofatlas$routePomSwitchDiffuse(
        String source,
        String diffuseSampler
    ) {
        Pattern definition =
            Pattern.compile(
                "\\bvec4\\s+texture2D_POMSwitch\\s*\\("
                + "\\s*sampler2D\\s+"
                + "([A-Za-z_][A-Za-z0-9_]*)\\s*,"
                + "\\s*vec2\\s+"
                + "([A-Za-z_][A-Za-z0-9_]*)\\s*,"
                + "\\s*vec4\\s+"
                + "([A-Za-z_][A-Za-z0-9_]*)\\s*,"
                + "\\s*bool\\s+"
                + "([A-Za-z_][A-Za-z0-9_]*)\\s*,"
                + "\\s*float\\s+"
                + "([A-Za-z_][A-Za-z0-9_]*)\\s*\\)"
            );

        Matcher definitionMatcher =
            definition.matcher(source);

        if (!definitionMatcher.find()) {
            return source;
        }

        boolean routedDiffuse =
            Pattern.compile(
                "\\btexture2D_POMSwitch\\s*\\(\\s*"
                + Pattern.quote(diffuseSampler)
                + "\\s*,"
            ).matcher(source).find();

        boolean routedNormal =
            Pattern.compile(
                "\\btexture2D_POMSwitch\\s*\\(\\s*normals\\s*,"
            ).matcher(source).find();

        boolean routedSpecular =
            Pattern.compile(
                "\\btexture2D_POMSwitch\\s*\\(\\s*specular\\s*,"
            ).matcher(source).find();

        if (
            !routedDiffuse
            && !routedNormal
            && !routedSpecular
        ) {
            return source;
        }

        if (routedDiffuse) {
            source =
                source.replaceAll(
                    "\\btexture2D_POMSwitch\\s*\\(\\s*"
                    + Pattern.quote(diffuseSampler)
                    + "\\s*,",
                    "pagesofatlas_texture2D_POMSwitch("
                );
        }

        if (routedNormal) {
            source =
                source.replaceAll(
                    "\\btexture2D_POMSwitch\\s*\\(\\s*normals\\s*,",
                    "pagesofatlas_normalTexture2D_POMSwitch("
                );
        }

        if (routedSpecular) {
            source =
                source.replaceAll(
                    "\\btexture2D_POMSwitch\\s*\\(\\s*specular\\s*,",
                    "pagesofatlas_specularTexture2D_POMSwitch("
                );
        }

        definitionMatcher =
            definition.matcher(source);

        if (!definitionMatcher.find()) {
            return source;
        }

        int insertAt =
            definitionMatcher.start();

        StringBuilder helper =
            new StringBuilder();

        if (routedDiffuse) {
            helper.append(
                "vec4 pagesofatlas_texture2D_POMSwitch("
                + "vec2 uv, vec4 dcdxdcdy, bool ifPOM, float LOD) {\n"
                + "    if (ifPOM) {\n"
                + "        return pagesofatlas_textureGrad("
                + "uv, dcdxdcdy.xy, dcdxdcdy.zw);\n"
                + "    }\n"
                + "    return pagesofatlas_textureLod(uv, LOD);\n"
                + "}\n\n"
            );
        }

        if (routedNormal) {
            helper.append(
                "vec4 pagesofatlas_normalTexture2D_POMSwitch("
                + "vec2 uv, vec4 dcdxdcdy, bool ifPOM, float LOD) {\n"
                + "    if (ifPOM) {\n"
                + "        return pagesofatlas_normalTextureGrad("
                + "uv, dcdxdcdy.xy, dcdxdcdy.zw);\n"
                + "    }\n"
                + "    return pagesofatlas_normalTexture(uv, LOD);\n"
                + "}\n\n"
            );
        }

        if (routedSpecular) {
            helper.append(
                "vec4 pagesofatlas_specularTexture2D_POMSwitch("
                + "vec2 uv, vec4 dcdxdcdy, bool ifPOM, float LOD) {\n"
                + "    if (ifPOM) {\n"
                + "        return pagesofatlas_specularTextureLod("
                + "uv, 0.0);\n"
                + "    }\n"
                + "    return pagesofatlas_specularTextureLod(uv, LOD);\n"
                + "}\n\n"
            );
        }

        return source.substring(0, insertAt)
            + helper
            + source.substring(insertAt);
    }

    private static String pagesofatlas$replacePbrTextureCalls(
        String source
    ) {
        /*
         * Only rewrite exact conventional terrain PBR samplers.
         * Other shader-pack samplers remain untouched.
         */
        /*
         * Modern GLSL normal-map sampling.
         */
        source =
            source.replaceAll(
                "\\btextureGrad\\s*\\(\\s*normals\\s*,",
                "pagesofatlas_normalTextureGrad("
            );

        source =
            source.replaceAll(
                "\\btexture\\s*\\(\\s*normals\\s*,",
                "pagesofatlas_normalTexture("
            );

        /*
         * Compatibility-profile form used heavily by
         * Complementary's material system.
         */
        source =
            source.replaceAll(
                "\\btexture2D\\s*\\(\\s*normals\\s*,",
                "pagesofatlas_normalTexture("
            );

        /*
         * Modern GLSL specular sampling.
         */
        source =
            source.replaceAll(
                "\\btexture\\s*\\(\\s*specular\\s*,",
                "pagesofatlas_specularTexture("
            );

        /*
         * Complementary uses texture2D() for its primary
         * specular/material lookup.
         */
        source =
            source.replaceAll(
                "\\btexture2D\\s*\\(\\s*specular\\s*,",
                "pagesofatlas_specularTexture("
            );

        /*
         * Complementary's labPBR emission path explicitly samples
         * LOD 0 to avoid mipmap-induced emission artifacts.
         */
        source =
            source.replaceAll(
                "\\btexture2DLod\\s*\\(\\s*specular\\s*,",
                "pagesofatlas_specularTextureLod("
            );

        source =
            source.replaceAll(
                "\\btextureLod\\s*\\(\\s*specular\\s*,",
                "pagesofatlas_specularTextureLod("
            );

        return source;
    }

    private static String pagesofatlas$insertPbrHelpersEarly(
        String source
    ) {
        boolean routedNormal =
            source.contains(
                "pagesofatlas_normalTexture"
            );

        boolean routedSpecular =
            source.contains(
                "pagesofatlas_specularTexture"
            );

        if (!routedNormal && !routedSpecular) {
            return source;
        }

        int callIndex =
            Integer.MAX_VALUE;

        int normalCall =
            source.indexOf(
                "pagesofatlas_normalTexture"
            );

        int specularCall =
            source.indexOf(
                "pagesofatlas_specularTexture"
            );

        if (normalCall >= 0) {
            callIndex =
                Math.min(
                    callIndex,
                    normalCall
                );
        }

        if (specularCall >= 0) {
            callIndex =
                Math.min(
                    callIndex,
                    specularCall
                );
        }

        Pattern functionPattern =
            Pattern.compile(
                "(?m)^[ \\t]*"
                + "(?:[A-Za-z_][A-Za-z0-9_]*[ \\t]+)+"
                + "[A-Za-z_][A-Za-z0-9_]*[ \\t]*"
                + "\\([^;{}]*\\)[ \\t]*\\{"
            );

        Matcher matcher =
            functionPattern.matcher(source);

        int insertAt =
            -1;

        while (
            matcher.find()
            && matcher.start() < callIndex
        ) {
            insertAt =
                matcher.start();
        }

        if (insertAt < 0) {
            return source;
        }

        String before =
            source.substring(
                0,
                insertAt
            );

        StringBuilder early =
            new StringBuilder();

        /*
         * pagesofatlas_page is shared by diffuse and PBR routing.
         *
         * Search the complete transformed shader rather than only
         * the region before this helper's insertion point. This
         * makes declaration injection idempotent when multiple POA
         * helper blocks are inserted at different locations.
         */
        if (!source.contains(
                "flat in uint pagesofatlas_page;")) {

            early.append(
                "flat in uint pagesofatlas_page;\n"
            );
        }

        /*
         * Iris already owns the native PBR companions for physical
         * diffuse page zero through its normals/specular samplers.
         *
         * POA therefore only needs custom deterministic PBR
         * companions for overflow pages 1-3.
         */
        for (int page = 1; page < 4; page++) {

            String normalDecl =
                "uniform sampler2D u_BlockNormalTex"
                    + page
                    + ";";

            if (!before.contains(normalDecl)) {
                early.append(
                    normalDecl
                        + "\n"
                );
            }

            String specularDecl =
                "uniform sampler2D u_BlockSpecularTex"
                    + page
                    + ";";

            if (!before.contains(specularDecl)) {
                early.append(
                    specularDecl
                        + "\n"
                );
            }
        }

        early.append("\n");

        if (routedNormal) {
            early.append(
                "vec4 pagesofatlas_normalTexture(vec2 uv) {\n"
                + "    if (pagesofatlas_page == 0u) {\n"
                + "        return texture(normals, uv);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return texture(u_BlockNormalTex1, uv);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return texture(u_BlockNormalTex2, uv);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return texture(u_BlockNormalTex3, uv);\n"
                + "    }\n"
                + "    return texture(normals, uv);\n"
                + "}\n\n"

                + "vec4 pagesofatlas_normalTexture(vec2 uv, float bias) {\n"
                + "    if (pagesofatlas_page == 0u) {\n"
                + "        return texture(normals, uv, bias);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return texture(u_BlockNormalTex1, uv, bias);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return texture(u_BlockNormalTex2, uv, bias);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return texture(u_BlockNormalTex3, uv, bias);\n"
                + "    }\n"
                + "    return texture(normals, uv, bias);\n"
                + "}\n\n"

                + "vec4 pagesofatlas_normalTextureGrad(vec2 uv, vec2 dx, vec2 dy) {\n"
                + "    if (pagesofatlas_page == 0u) {\n"
                + "        return textureGrad(normals, uv, dx, dy);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return textureGrad(u_BlockNormalTex1, uv, dx, dy);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return textureGrad(u_BlockNormalTex2, uv, dx, dy);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return textureGrad(u_BlockNormalTex3, uv, dx, dy);\n"
                + "    }\n"
                + "    return textureGrad(normals, uv, dx, dy);\n"
                + "}\n\n"
            );
        }

        if (routedSpecular) {
            early.append(
                "vec4 pagesofatlas_specularTexture(vec2 uv) {\n"
                + "    if (pagesofatlas_page == 0u) {\n"
                + "        return texture(specular, uv);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return texture(u_BlockSpecularTex1, uv);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return texture(u_BlockSpecularTex2, uv);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return texture(u_BlockSpecularTex3, uv);\n"
                + "    }\n"
                + "    return texture(specular, uv);\n"
                + "}\n\n"

                + "vec4 pagesofatlas_specularTexture(vec2 uv, float bias) {\n"
                + "    if (pagesofatlas_page == 0u) {\n"
                + "        return texture(specular, uv, bias);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return texture(u_BlockSpecularTex1, uv, bias);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return texture(u_BlockSpecularTex2, uv, bias);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return texture(u_BlockSpecularTex3, uv, bias);\n"
                + "    }\n"
                + "    return texture(specular, uv, bias);\n"
                + "}\n\n"

                + "vec4 pagesofatlas_specularTextureLod(vec2 uv, float lod) {\n"
                + "    if (pagesofatlas_page == 0u) {\n"
                + "        return textureLod(specular, uv, lod);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 1u) {\n"
                + "        return textureLod(u_BlockSpecularTex1, uv, lod);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 2u) {\n"
                + "        return textureLod(u_BlockSpecularTex2, uv, lod);\n"
                + "    }\n"
                + "    if (pagesofatlas_page == 3u) {\n"
                + "        return textureLod(u_BlockSpecularTex3, uv, lod);\n"
                + "    }\n"
                + "    return textureLod(specular, uv, lod);\n"
                + "}\n\n"
            );
        }

        return source.substring(
                0,
                insertAt
            )
            + early
            + source.substring(
                insertAt
            );
    }

    private static String pagesofatlas$replaceTextureCalls(
        String source,
        String sampler
    ) {
        /*
         * Whitespace-tolerant replacements:
         *
         * texture(tex, uv)
         * texture ( tex , uv )
         * textureLod(gtexture, uv, lod)
         *
         * all become Pages of Atlas routed calls.
         */
        String samplerPattern =
            Pattern.quote(
                sampler
            );

        source =
            source.replaceAll(
                "\\btextureGrad\\s*\\(\\s*"
                + samplerPattern
                + "\\s*,",
                "pagesofatlas_textureGrad("
            );

        source =
            source.replaceAll(
                "\\btexture\\s*\\(\\s*"
                + samplerPattern
                + "\\s*,",
                "pagesofatlas_texture("
            );

        source =
            source.replaceAll(
                "\\btextureLod\\s*\\(\\s*"
                + samplerPattern
                + "\\s*,",
                "pagesofatlas_textureLod("
            );

        return source;
    }
}
