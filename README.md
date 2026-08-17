# Pages of Atlas

**Pages of Atlas** is a Minecraft Fabric mod that enables ultra-high-resolution resource packs by transparently distributing oversized texture atlases across multiple physical atlas pages.

It is designed for high-resolution resource packs whose combined texture atlas
would otherwise exceed the maximum texture size supported by the user's GPU.

Instead of requiring the entire block atlas to fit inside a single enormous GPU
texture, Pages of Atlas divides it into multiple physical atlas pages and routes
rendering to the appropriate page.

## Why Pages of Atlas Exists

High-resolution Minecraft resource packs can contain thousands of large textures.

Minecraft normally stitches these textures into texture atlases before uploading
them to the GPU. With sufficiently large resource packs, the resulting atlas can
exceed the GPU's maximum supported texture dimensions.

For example, a GPU with a maximum texture size of 16384x16384 cannot upload a
single 32768x32768 block atlas, even if the card has enough total VRAM to store
the textures.

This creates an architectural limitation rather than simply a VRAM limitation.

Pages of Atlas addresses this by allowing the logical Minecraft atlas to span
multiple physical GPU textures.

Conceptually:

    Minecraft logical block atlas
              |
              v
    +-----------------------+
    |    Pages of Atlas     |
    +-----------------------+
       |        |        |
       v        v        v
    Page 0   Page 1   Page 2 ...
       |        |        |
       +--------+--------+
                |
                v
             Rendering

Each sprite retains information about which physical atlas page contains it.
During rendering, Pages of Atlas selects the appropriate page while preserving
the texture coordinates expected by Minecraft's renderer.

## Goals

Pages of Atlas is intended to:

- Allow extremely high-resolution resource packs to exceed the GPU's
  single-texture dimension limit.
- Support multiple physical block-atlas pages.
- Preserve normal Minecraft terrain rendering.
- Support solid, cutout, and translucent block rendering.
- Support block-item rendering from paged atlases.
- Remain compatible with modern Fabric rendering stacks where practical.
- Avoid requiring changes to the resource pack itself.
- Remain resource-pack agnostic rather than being tied to one specific pack.

Pages of Atlas does **not** provide textures.

It provides the rendering infrastructure that allows sufficiently large texture
packs to operate across multiple physical atlas textures.

## Compatibility

Pages of Atlas is designed as a client-side rendering mod.

Current development has focused on compatibility with the modern Fabric
rendering ecosystem, including configurations involving:

- Sodium
- Iris
- Continuity
- LabPBR resource-pack workflows
- Shader packs

High-resolution packs such as Patrix have been used extensively during
development and testing, but Pages of Atlas is not designed specifically for
Patrix.

Other resource packs that exceed the normal block-atlas texture-size limit may
also benefit from Pages of Atlas.

Resource-pack-specific features can still require their normal companion mods.
For example, connected or blended textures may require Continuity or another
compatible implementation.

## Shaders

Pages of Atlas includes support for shader-based rendering paths used by modern
Minecraft shader configurations.

Testing has included shader environments such as:

- Complementary
- SEUS PBR
- BSL

Shader compatibility is an active area of development. Because shader packs can
implement their rendering pipelines differently, individual shader packs may
still expose compatibility issues.

If you encounter a shader-specific problem, please include the shader name and
version when reporting the issue.

## Requirements

Pages of Atlas currently targets:

- Minecraft 26.2
- Fabric Loader
- Java 25

Additional rendering or resource-pack mods may be required depending on the
resource pack and shader configuration being used.

## Installation

1. Install Fabric Loader for the supported Minecraft version.
2. Install any additional mods required by your resource pack or shader setup.
3. Download the latest Pages of Atlas release.
4. Place the Pages of Atlas JAR in the Minecraft instance's `mods/` directory.
5. Enable the desired high-resolution resource pack.
6. Launch Minecraft normally.

For Prism Launcher, the JAR belongs in the instance's:

    minecraft/mods/

directory.

Pages of Atlas is client-side. A multiplayer server does not need Pages of Atlas
installed simply because a client uses it for a high-resolution resource pack.

## How It Works

Minecraft normally treats a stitched texture atlas as one GPU texture.

Pages of Atlas introduces a paging layer between Minecraft's logical atlas and
the physical textures uploaded to the GPU.

During atlas construction, sprites are assigned to physical pages that remain
within the GPU's supported texture dimensions.

Pages of Atlas then tracks information including:

- the physical page containing each sprite
- the sprite's coordinates within that page
- the physical texture associated with that page
- the rendering state necessary to select the correct page

Rendering code can then use the correct physical atlas page without requiring
the resource pack to reorganize its textures manually.

This allows the effective logical atlas to exceed the maximum dimensions of any
single GPU texture.

## GPU Limits

Pages of Atlas does not eliminate GPU limitations.

It specifically works around the **maximum dimensions of an individual texture**.

The GPU must still have enough memory and bandwidth to store and render the
resource pack.

For example, splitting a large atlas into several 16384x16384 textures makes it
possible to use an atlas that could not exist as one physical texture, but the
combined pages can still consume a substantial amount of VRAM.

Higher-resolution resource packs therefore remain demanding.

Pages of Atlas removes one important architectural ceiling; it does not make
high-resolution textures free.

## Performance

Performance depends heavily on:

- resource-pack resolution
- number of textures
- number of physical atlas pages
- available VRAM
- GPU memory bandwidth
- shader pack
- shader settings
- render distance
- other rendering mods

Pages of Atlas is intended to make otherwise impossible atlas configurations
possible while keeping the paging overhead as small as practical.

Users should still choose resource-pack resolutions appropriate for their
hardware.

## Project Status

Pages of Atlas is under active development.

The paging architecture is functional, but compatibility testing continues
across:

- resource packs
- GPUs
- Sodium versions
- Iris versions
- shader packs
- Minecraft updates

Rendering mods frequently modify the same parts of Minecraft's rendering
pipeline that Pages of Atlas interacts with, so compatibility work is expected
to remain an important part of development.

Bug reports and reproducible test cases are welcome.

## Reporting Issues

When reporting a rendering problem, please include as much of the following as
possible:

- Minecraft version
- Pages of Atlas version
- Fabric Loader version
- GPU model
- GPU driver
- operating system
- resource pack and resolution
- Sodium version, if installed
- Iris version, if installed
- shader pack and version, if enabled
- relevant Minecraft log
- screenshots showing the rendering problem

For atlas-related problems, the Minecraft log is particularly useful because
Pages of Atlas records information about atlas construction and physical page
creation.

## Resource-Pack Authors

Pages of Atlas is intended to operate transparently beneath the resource-pack
layer.

Resource-pack authors should not need to manually divide their textures into
Pages of Atlas pages.

The mod performs paging after Minecraft has gathered the textures required for
the logical atlas.

This means Pages of Atlas can potentially support many high-resolution resource
packs without requiring pack-specific integration.

## Developers

Pages of Atlas modifies low-level portions of Minecraft's client rendering and
texture-atlas pipeline.

Areas involved include:

- sprite loading
- atlas stitching
- texture upload
- sprite metadata
- render-state selection
- terrain rendering
- block-item rendering
- shader interaction

Because these systems change between Minecraft versions, Pages of Atlas should
currently be considered version-specific rendering infrastructure rather than a
version-independent API.

## Name

The name **Pages of Atlas** refers to the project's central concept:

Minecraft sees one logical atlas, while the GPU can hold that atlas as multiple
physical **pages**.

## License

Pages of Atlas is licensed under the MIT License.

See `LICENSE` for details.
