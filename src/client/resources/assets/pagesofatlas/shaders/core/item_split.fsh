#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;

in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;

in vec2 texCoord0;

out vec4 fragColor;

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

    vec4 color;

    if (page == 1) {
        color =
            texture(
                Sampler3,
                uv
            );

    } else if (page == 2) {
        color =
            texture(
                Sampler4,
                uv
            );

    } else if (page == 3) {
        color =
            texture(
                Sampler5,
                uv
            );

    } else {
        color =
            texture(
                Sampler0,
                uv
            );
    }

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *=
        vertexColor
        * ColorModulator;

    color.rgb =
        mix(
            overlayColor.rgb,
            color.rgb,
            overlayColor.a
        );

    color *=
        lightMapColor;

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
