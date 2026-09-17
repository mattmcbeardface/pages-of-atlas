#version 330 core

#moj_import <sodium:globals.glsl>
#moj_import <sodium:fog.glsl>
#moj_import <sodium:chunk_material.glsl>

in vec4 v_Color;
in vec2 v_TexCoord;
flat in uint v_PagesOfAtlasPage;
in vec2 v_FragDistance;
in float fadeFactor;

uniform sampler2D u_BlockTex;
uniform sampler2D u_BlockTex1;
uniform sampler2D u_BlockTex2;
uniform sampler2D u_BlockTex3;

out vec4 fragColor;

vec4 pagesofatlasSample(
    sampler2D source,
    vec2 uv
) {
    /* Local interpolation preserves page-correct implicit derivatives/LOD. */
    return texture(source, uv);
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

    fragColor = _linearFog(
        color,
        v_FragDistance,
        u_FogColor,
        u_EnvironmentFog,
        u_RenderFog,
        fadeFactor
    );
}
