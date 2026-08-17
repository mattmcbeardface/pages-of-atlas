#version 330 core

#moj_import <sodium:globals.glsl>
#moj_import <sodium:fog.glsl>
#moj_import <sodium:chunk_vertex.glsl>

out vec4 v_Color;
out vec2 v_TexCoord;

flat out uint v_PagesOfAtlasPage;

#ifdef USE_FOG
out vec2 v_FragDistance;
out float fadeFactor;
#endif

uniform isamplerBuffer u_SectionTimeInfo;

#ifdef VULKAN
layout(push_constant) uniform PC {
    vec3 u_RegionOffset;
    int u_CurrentTime;
    uint u_RegionID;
};
#else
uniform vec3 u_RegionOffset;
uniform int u_CurrentTime;
uniform uint u_RegionID;
#endif

uniform sampler2D u_LightTex;

uvec3 _get_relative_chunk_coord(uint pos) {
    return uvec3(pos) >>
        uvec3(5u, 0u, 2u) &
        uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) *
        vec3(16.0);
}

void main() {
    _vert_init();

    /*
     * Sodium owns bits 0-2.
     * Pages of Atlas owns bits 3-4.
     */
    v_PagesOfAtlasPage =
        (_material_params >> 3u) & 3u;

    vec3 translation =
        u_RegionOffset +
        _get_draw_translation(_draw_id);

    vec3 position =
        _vert_position +
        translation;

#ifdef USE_FOG
    v_FragDistance =
        getFragDistance(position);

    int chunkId =
        int(_draw_id);

    int chunkFade =
        texelFetch(
            u_SectionTimeInfo,
            int(
                (u_RegionID * 256u) +
                uint(chunkId)
            )
        ).r;

    int fadeTime =
        u_CurrentTime -
        chunkFade;

    float elapsed =
        float(fadeTime);

    float fade =
        clamp(
            float(
                u_CurrentTime -
                chunkFade
            ) *
            u_FadePeriodInv,
            0.0,
            1.0
        );

    fadeFactor =
        (chunkFade < 0)
            ? 1.0
            : fade;
#endif

    gl_Position =
        u_ProjectionMatrix *
        u_ModelViewMatrix *
        vec4(position, 1.0);

    v_Color =
        _vert_color *
        texture(
            u_LightTex,
            _vert_tex_light_coord
        );

    v_TexCoord =
        (_vert_tex_diffuse_coord_bias *
            u_TexCoordShrink) +
        _vert_tex_diffuse_coord;
}
