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
        color = texture(u_BlockTex1, v_TexCoord);
    } else if (v_PagesOfAtlasPage == 2u) {
        color = texture(u_BlockTex2, v_TexCoord);
    } else if (v_PagesOfAtlasPage == 3u) {
        color = texture(u_BlockTex3, v_TexCoord);
    } else {
        color = texture(u_BlockTex, v_TexCoord);
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
