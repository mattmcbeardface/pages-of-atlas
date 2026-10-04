#version 460 core

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_material.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;
layout(location = 2) in vec2 v_FragDistance;
layout(location = 3) in float fadeFactor;
layout(location = 4) flat in uint v_PagesOfAtlasPage;

uniform sampler2D u_BlockTex;
uniform sampler2D u_BlockTex1;
uniform sampler2D u_BlockTex2;
uniform sampler2D u_BlockTex3;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 sampleNearest(
    sampler2D source,
    vec2 uv,
    vec2 pixelSize,
    vec2 du,
    vec2 dv,
    vec2 texelScreenSize
) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset =
        (texelOffset - 0.5f)
        * pixelSize
        / texelScreenSize
        + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(
    sampler2D source,
    vec2 uv,
    vec2 pixelSize
) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(
        source,
        uv,
        pixelSize,
        du,
        dv,
        texelScreenSize
    );
}

vec4 sampleRGSS(
    sampler2D source,
    vec2 uv,
    vec2 pixelSize
) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize =
        max(texelScreenSize.x, texelScreenSize.y);
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float blendFactor = smoothstep(
        minPixelSize,
        minPixelSize * 2.0,
        maxTexelSize
    );
    float mipLevelExact = max(
        0.0,
        log2(
            sqrt(length(du) * length(dv))
            / minPixelSize
        )
    );

    const vec2 offsets[4] = vec2[](
        vec2(0.125, 0.375),
        vec2(-0.125, -0.375),
        vec2(0.375, -0.125),
        vec2(-0.375, 0.125)
    );

    vec4 rgssColor = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        rgssColor += textureLod(
            source,
            uv + offsets[i] * pixelSize,
            mipLevelExact
        );
    }
    rgssColor *= 0.25;

    return mix(
        sampleNearest(
            source,
            uv,
            pixelSize,
            du,
            dv,
            texelScreenSize
        ),
        rgssColor,
        blendFactor
    );
}

vec4 pagesofatlasSample(sampler2D source, vec2 uv) {
    return u_UseRGSS
        ? sampleRGSS(source, uv, u_TexelSize)
        : sampleNearest(source, uv, u_TexelSize);
}

vec4 pagesofatlasFinalColor(vec4 color) {
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor =
        vec4(u_FogColor.rgb * color.a, u_FogColor.a);
#else
    vec4 fogColor = u_FogColor;
#endif

#ifdef OIT_ALPHA_ONLY
    float factor = 1.0;
#else
    float factor = fadeFactor;
#endif

    return _linearFog(
        color,
        v_FragDistance,
        fogColor,
        u_EnvironmentFog,
        u_RenderFog,
        factor
    );
}

void main() {
    vec4 color;

    if (v_PagesOfAtlasPage == 1u) {
        color = pagesofatlasSample(u_BlockTex1, v_TexCoord);
    } else if (v_PagesOfAtlasPage == 2u) {
        color = pagesofatlasSample(u_BlockTex2, v_TexCoord);
    } else if (v_PagesOfAtlasPage == 3u) {
        color = pagesofatlasSample(u_BlockTex3, v_TexCoord);
    } else {
        color = pagesofatlasSample(u_BlockTex, v_TexCoord);
    }

    color *= v_Color;

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    fragColor = pagesofatlasFinalColor(color);
#endif
}
