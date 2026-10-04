# Changelog

Notable user-facing changes to Pages of Atlas are documented here.

## [0.4.0] - 2026-10-03

0.4.0 replaces the previous direct multi-page block-atlas rendering model with
a production virtual-atlas architecture for oversized resource packs.

### Added

- Added a fixed 32768x32768 logical block-atlas address space backed by four
  independently allocated physical atlas pages, each capped at the GPU-safe
  16384x16384 texture size.
- Added automatic virtual-atlas activation when the block atlas requires more
  than one physical texture. Resource packs that fit on one page continue to
  use Minecraft's normal single-atlas path.
- Added logical virtual sprites so Minecraft, Fabric Renderer API, Sodium, and
  Continuity can operate in one continuous sprite/UV space while Pages of
  Atlas retains separate physical sprites for GPU upload.
- Added dedicated virtual-atlas terrain shaders for Minecraft and Sodium.
- Added page-aware item and block-model rendering for vanilla/Fabric Renderer
  API, Indigo, and Sodium rendering paths.
- Added page-aware Sodium extended block-model rendering used by item frames
  and other block-model feature renderers.
- Added generation-aware publication of virtual atlas plans and uploads so an
  atlas generation becomes active only after its physical-page state is ready.
- Added optional virtual-atlas diagnostics for validating logical placement,
  physical-page routing, and UV conversion.

### Changed

- Reworked oversized block-atlas packing around one logical atlas instead of
  exposing physical pages directly to normal sprite lookup and rendering.
- Terrain now carries or derives physical-page ownership from logical atlas
  UVs and converts those coordinates to page-local UVs at render time.
- Reworked block-item rendering to resolve the correct physical page per quad.
- Updated Fabric Renderer API and Indigo quad handling for logical-to-physical
  page translation.
- Updated Sodium sprite lookup and terrain rendering for the logical atlas.
- Updated Continuity CTM lookup to search the combined logical sprite space.
- Expanded Iris shader transformation support for virtual-atlas UV conversion,
  physical-page sampling, and PBR companion textures.
- Preserved normal/specular PBR page alignment with the corresponding diffuse
  physical page.
- Removed the previous proof-of-concept activation path; oversized block
  atlases now select the production virtual renderer automatically.

### Fixed

- Fixed item-frame and Sodium extended block-model rendering when a block
  texture resides on a secondary physical atlas page.
- Fixed logical sprite lookup for Fabric Renderer API, Sodium, and Continuity
  while retaining physical page-zero state required by upload and PBR code.
- Fixed page-local terrain sampling so implicit derivatives and mip selection
  remain correct after virtual-to-physical UV conversion.
- Fixed Photon POM coordinate routing through the virtual atlas.
- Fixed Solas compatibility for shader programs whose mid-texture-coordinate
  inputs differ from other Iris shader packs.

### Compatibility

Validated on Minecraft 26.2 with high-resolution Patrix resource packs,
Sodium, Iris, Continuity, Photon, Solas, and Complementary / Unbound.

### Minecraft 26.3

The Minecraft 26.3 build ports the 0.4.0 virtual-atlas renderer to the new
RenderPearl rendering backend.

- Updated GPU texture, render-pass, pipeline, and bind-group integration for
  RenderPearl.
- Added Minecraft 26.3 multidraw terrain support.
- Added 26.3 OIT/translucency rendering support.
- Updated Sodium integration for 0.9.3-alpha.1.
- Updated Iris integration for 1.11.7.
- Updated Fabric Renderer API integration for Minecraft 26.3.
- Updated item rendering for the 26.3 vertex format, including UV3 forwarding.
- Removed the obsolete manual OpenGL sampler-unit remapping; RenderPearl now
  manages sampler bindings directly.
- Validated with Patrix, Photon/POM, Solas, Complementary / Unbound, and
  Shrimple.

## [0.3.15] - 2026-09-07

### Fixed

- Fixed block-item textures on secondary physical atlas pages rendering gray
  in inventory and hotbar GUI slots.
- Aligned page-specific item pipelines with Minecraft 26.2's native item
  shader and complete diffuse, overlay, and lightmap sampler contract.
- Preserved per-quad physical-page routing for item models whose quads span
  multiple atlas pages.

## [0.3.14] - 2026-08-29

### Fixed

- Prevented failed PBR atlas pages from retrying expensive builds every
  rendered frame.
- Stopped requests for PBR pages that do not exist in the active atlas plan.
- Released obsolete secondary atlas textures when switching to a
  lower-resolution or smaller resource-pack configuration.
- Safely recreated secondary atlas pages when switching back to a multi-page
  configuration.
- Staged replacement atlas generations until every physical page uploaded
  successfully, preventing render access to uninitialized texture views.
- Moved PBR and physical-atlas GPU cleanup onto the render thread.
- Removed obsolete per-quad Sodium diagnostics and temporary atlas-placement
  logging.
- Corrected the Mod Menu icon dimensions to 330×330.

### Performance and memory

- Eliminated repeated nonexistent-page work from the terrain-rendering path.
- Removed diagnostic atomic contention and `ThreadLocal` entry churn from
  Sodium chunk compilation.
- Prevented obsolete secondary atlases from retaining unnecessary VRAM after
  resource reloads.

## [0.3.13] - 2026-08-27

### Fixed

- Reworked Iris multi-page item rendering so atlas pages are selected through
  render state rather than shader-pack GLSL transformation, restoring broad
  shader compatibility and correct multi-page GUI item rendering.

## [0.3.12] - 2026-08-27

### Fixed

- Fixed Iris compatibility with PoA multi-page item rendering.
- Fixed the resulting OpenGL debug-message/log flood.
- Preserved correct multi-page GUI/item and particle rendering.

## [0.3.11] - 2026-08-25

### Added

- Added dedicated Minecraft 26.2 painting-atlas paging support for
  high-resolution painting resource packs.
- Added combined logical painting-sprite lookup across all physical pages.

### Fixed

- Fixed painting fronts assigned to secondary atlas pages resolving to
  Minecraft's magenta-and-black missing texture.
- Routed each painting render to the physical atlas page containing its front
  sprite while preserving Minecraft's existing single-texture submission.
- Replicated `minecraft:back` on every physical painting page so painting
  backs and edges use the same bound texture and page-local UV coordinates as
  the front.
- Preserved atlas ownership boundaries so shared painting sprite contents are
  closed exactly once during resource reloads and shutdown.

### Changed

- Replaced the green-and-beige project icon with the new blue layered-page
  artwork.

### Compatibility

- Retains the existing block, item, PBR, Iris, Sodium, and Continuity behavior.
- Targets Minecraft 26.2, Fabric Loader 0.19.3 or newer, Fabric API 0.156.0 or
  newer, and Java 25.

[0.4.0]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.4.0-26.3
[0.3.15]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.15
[0.3.14]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.14
[0.3.13]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.13
[0.3.12]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.12
[0.3.11]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.11
