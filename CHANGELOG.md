# Changelog

Notable user-facing changes to Pages of Atlas are documented here.

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

[0.3.11]: https://github.com/mattmcbeardface/pages-of-atlas/releases/tag/v0.3.11
