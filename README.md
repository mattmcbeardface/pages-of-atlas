# SplitAtlas

SplitAtlas is a client-side Fabric mod for Minecraft 26.2 that allows oversized
texture atlases to be split across multiple GPU textures.

It was created to allow very large high-resolution resource packs to load on
hardware whose maximum OpenGL texture size is smaller than the resource pack's
combined block texture atlas.

## Current status

SplitAtlas 0.1.0 is an early release.

Current support includes:

- Oversized block texture atlases
- Multiple physical atlas pages
- Terrain rendering across atlas pages
- Solid, cutout, and translucent block rendering
- Block-item rendering from secondary atlas pages
- High-resolution resource packs such as Patrix 256x

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Java 25

Fabric API is not required by the release JAR.

## Installation

Place:

    splitatlas-0.1.0.jar

in the Minecraft instance's:

    mods/

directory.

For Prism Launcher, open the instance folder and place the JAR in:

    minecraft/mods/

## Resource packs

SplitAtlas does not provide textures itself.

It allows resource packs whose block atlas would otherwise exceed the GPU's
maximum supported texture size to be divided into multiple physical atlas
textures.

Other resource-pack features may still require their normal companion mods.

For example, Patrix may require additional mods such as Continuity for connected
or blended textures.

## Limitations

SplitAtlas 0.1.0 currently targets the block atlas implementation used by
Minecraft 26.2.

The current renderer supports two physical block-atlas pages:

- Page 0
- Page 1

Additional atlas pages are not yet supported by the shader path.

Entity textures that are stored as independent textures are not affected by
SplitAtlas.

## License

MIT
