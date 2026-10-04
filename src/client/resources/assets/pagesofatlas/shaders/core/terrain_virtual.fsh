#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) flat in int pagesofatlasPage;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 pagesofatlasFinalColor(vec4 color) {
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor =
        vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif

    return apply_fog(
        color,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        fogColor
    );
}

void main() {
    vec4 sampled;

    if (pagesofatlasPage == 1) {
        sampled = texture(Sampler1, texCoord0);
    } else if (pagesofatlasPage == 2) {
        sampled = texture(Sampler3, texCoord0);
    } else if (pagesofatlasPage == 3) {
        sampled = texture(Sampler4, texCoord0);
    } else {
        sampled = texture(Sampler0, texCoord0);
    }

    vec4 color = sampled * vertexColor;

#ifndef OIT_ALPHA_ONLY
    color = mix(
        FogColor * vec4(1, 1, 1, color.a),
        color,
        chunkVisibility
    );
#endif

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
