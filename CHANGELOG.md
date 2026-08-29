# Changelog

Notable user-facing changes to Pages of Atlas are documented here.

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

[0.3.14]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.14
[0.3.13]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.13
[0.3.12]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.12
[0.3.11]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.11
