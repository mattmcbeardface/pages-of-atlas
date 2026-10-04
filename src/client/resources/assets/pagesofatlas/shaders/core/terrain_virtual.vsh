#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
layout(location = 5) flat out int pagesofatlasPage;

vec2 pagesofatlasPhysicalSize(int page) {
    if (page == 1) {
        return vec2(textureSize(Sampler1, 0));
    }
    if (page == 2) {
        return vec2(textureSize(Sampler3, 0));
    }
    if (page == 3) {
        return vec2(textureSize(Sampler4, 0));
    }
    return vec2(textureSize(Sampler0, 0));
}

void main() {
    vec3 pos =
        Position
        + (ChunkPosition - CameraBlockPos)
        + CameraOffset;

    gl_Position =
        ProjMat
        * ModelViewMat
        * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

#ifndef OIT_ALPHA_ONLY
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif

    vec2 virtualPixel = UV0 * 32768.0;
    ivec2 pageCell =
        ivec2(floor(virtualPixel / 16384.0));

    pagesofatlasPage =
        pageCell.y * 2 + pageCell.x;

    vec2 pagePixel =
        virtualPixel
        - vec2(pageCell) * 16384.0;

    texCoord0 =
        pagePixel
        / pagesofatlasPhysicalSize(pagesofatlasPage);

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(
        1.0,
        ChunkVisibility,
        clamp(
            (dist - chunkFullyVisibleRange)
                / chunkFullyVisibleRange,
            0.0,
            1.0
        )
    );
}
