#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec4 sampleNearest(
    sampler2D source,
    vec2 uv,
    vec2 pixelSize,
    vec2 du,
    vec2 dv,
    vec2 texelScreenSize
) {
    vec2 uvTexelCoords =
        uv / pixelSize;

    vec2 texelCenter =
        round(uvTexelCoords) - 0.5f;

    vec2 texelOffset =
        uvTexelCoords - texelCenter;

    texelOffset =
        (texelOffset - 0.5f)
        * pixelSize
        / texelScreenSize
        + 0.5f;

    texelOffset =
        clamp(
            texelOffset,
            0.0f,
            1.0f
        );

    uv =
        (texelCenter + texelOffset)
        * pixelSize;

    return textureGrad(
        source,
        uv,
        du,
        dv
    );
}

vec4 sampleNearest(
    sampler2D source,
    vec2 uv,
    vec2 pixelSize
) {
    vec2 du =
        dFdx(uv);

    vec2 dv =
        dFdy(uv);

    vec2 texelScreenSize =
        sqrt(
            du * du
            + dv * dv
        );

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
    vec2 du =
        dFdx(uv);

    vec2 dv =
        dFdy(uv);

    vec2 texelScreenSize =
        sqrt(
            du * du
            + dv * dv
        );

    float maxTexelSize =
        max(
            texelScreenSize.x,
            texelScreenSize.y
        );

    float minPixelSize =
        min(
            pixelSize.x,
            pixelSize.y
        );

    float transitionStart =
        minPixelSize;

    float transitionEnd =
        minPixelSize * 2.0;

    float blendFactor =
        smoothstep(
            transitionStart,
            transitionEnd,
            maxTexelSize
        );

    float duLength =
        length(du);

    float dvLength =
        length(dv);

    float minDerivative =
        min(
            duLength,
            dvLength
        );

    float maxDerivative =
        max(
            duLength,
            dvLength
        );

    float effectiveDerivative =
        sqrt(
            minDerivative
            * maxDerivative
        );

    float mipLevelExact =
        max(
            0.0,
            log2(
                effectiveDerivative
                / minPixelSize
            )
        );

    float mipLevelLow =
        floor(
            mipLevelExact
        );

    float mipLevelHigh =
        mipLevelLow + 1.0;

    float mipBlend =
        fract(
            mipLevelExact
        );

    const vec2 offsets[4] =
        vec2[](
            vec2(0.125, 0.375),
            vec2(-0.125, -0.375),
            vec2(0.375, -0.125),
            vec2(-0.375, 0.125)
        );

    vec4 rgssColorLow =
        vec4(0.0);

    vec4 rgssColorHigh =
        vec4(0.0);

    for (int i = 0; i < 4; ++i) {

        vec2 sampleUV =
            uv
            + offsets[i]
            * pixelSize;

        rgssColorLow +=
            textureLod(
                source,
                sampleUV,
                mipLevelLow
            );

        rgssColorHigh +=
            textureLod(
                source,
                sampleUV,
                mipLevelHigh
            );
    }

    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;

    vec4 rgssColor =
        mix(
            rgssColorLow,
            rgssColorHigh,
            mipBlend
        );

    vec4 nearestColor =
        sampleNearest(
            source,
            uv,
            pixelSize,
            du,
            dv,
            texelScreenSize
        );

    return mix(
        nearestColor,
        rgssColor,
        blendFactor
    );
}

vec4 samplePage(
    sampler2D source,
    vec2 uv
) {
    vec2 pixelSize =
        1.0
        / vec2(
            textureSize(
                source,
                0
            )
        );

    return UseRgss == 1
        ? sampleRGSS(
            source,
            uv,
            pixelSize
        )
        : sampleNearest(
            source,
            uv,
            pixelSize
        );
}

void main() {

    /*
     * Encoded UV ranges:
     *
     * page 0 -> U + 0
     * page 1 -> U + 2
     * page 2 -> U + 4
     * page 3 -> U + 6
     */
    int page =
        int(
            floor(
                texCoord0.x / 2.0
            )
        );

    vec2 uv =
        texCoord0;

    uv.x -=
        float(page)
        * 2.0;

    vec4 sampled;

    if (page == 1) {
        sampled =
            samplePage(
                Sampler1,
                uv
            );

    } else if (page == 2) {
        sampled =
            samplePage(
                Sampler3,
                uv
            );

    } else if (page == 3) {
        sampled =
            samplePage(
                Sampler4,
                uv
            );

    } else {
        sampled =
            samplePage(
                Sampler0,
                uv
            );
    }

    vec4 color =
        sampled
        * vertexColor;

    color =
        mix(
            FogColor
                * vec4(
                    1,
                    1,
                    1,
                    color.a
                ),
            color,
            ChunkVisibility
        );

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    fragColor =
        apply_fog(
            color,
            sphericalVertexDistance,
            cylindricalVertexDistance,
            FogEnvironmentalStart,
            FogEnvironmentalEnd,
            FogRenderDistanceStart,
            FogRenderDistanceEnd,
            FogColor
        );
}
