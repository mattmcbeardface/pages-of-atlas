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
flat in int pagesofatlasPage;

out vec4 fragColor;

vec4 pagesofatlasSample(
    sampler2D source,
    vec2 uv
) {
    /*
     * page-local UV is interpolated, so implicit derivatives and ordinary
     * hardware mip selection remain valid within the selected page.
     */
    return texture(source, uv);
}

void main() {
    vec4 sampled;

    if (pagesofatlasPage == 1) {
        sampled = pagesofatlasSample(Sampler1, texCoord0);
    } else if (pagesofatlasPage == 2) {
        sampled = pagesofatlasSample(Sampler3, texCoord0);
    } else if (pagesofatlasPage == 3) {
        sampled = pagesofatlasSample(Sampler4, texCoord0);
    } else {
        sampled = pagesofatlasSample(Sampler0, texCoord0);
    }

    vec4 color =
        sampled * vertexColor;

    color =
        mix(
            FogColor * vec4(1, 1, 1, color.a),
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
