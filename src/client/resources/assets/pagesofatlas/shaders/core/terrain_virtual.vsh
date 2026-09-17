#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out int pagesofatlasPage;

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

    sphericalVertexDistance =
        fog_spherical_distance(pos);

    cylindricalVertexDistance =
        fog_cylindrical_distance(pos);

    vertexColor =
        Color
        * sample_lightmap(Sampler2, UV2);

    vec2 virtualPixel =
        UV0 * 32768.0;

    ivec2 pageCell =
        ivec2(floor(virtualPixel / 16384.0));

    pagesofatlasPage =
        pageCell.y * 2 + pageCell.x;

    vec2 pagePixel =
        virtualPixel
        - vec2(pageCell) * 16384.0;

    texCoord0 =
        pagePixel
        / pagesofatlasPhysicalSize(
            pagesofatlasPage
        );
}
