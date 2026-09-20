# More Modern Structures (Minecraft 1.21.4)
> **Language:** [Русский](README.md) · English

A mod that adds randomly generated ultra-modern buildings to the world. Only vanilla Minecraft 1.21.4 blocks are used, so the mod does not conflict with other mods and requires no third-party libraries (except [Fabric API](https://modrinth.com/mod/fabric-api)).

## Content
---
Generated structures are registered as dedicated `structure_set` entries (jigsaw, `random_spread`, spacing 45 / separation 25) in biomes: plains, savannas, deserts, forests, taigas, hills, mountains, mushroom fields and others.

### Landmark building "Cat Base"
---
A modern base/shelter 22×17×27 in size, ported from the [Cat Base.litematic] schematic. Converted automatically (palette size 60, block entities missing from 1.21.4 skipped).
### Skyscrapers from the Wild Cities datapack
---
Four towers decorated by the **Wild Cities** datapack (MIT license). Assembled from its `base`, `middle` and `roof` jigsaw parts into single complete structures: tower heights are 24, 24, 33 and 17 blocks (16×16 footprint).

### Imported schematics (source: PlanetMinecraft)
---
Buildings imported from user-downloaded schematics and converted to vanilla structures via `tools/SchemaTools.java` (modes `schem`, `litem`, `norm`):

| Structure | Size | Description |
|---|---|---|
| `pale_oak_house` | 21×11×20 | Modern pale-oak house |
| `apartment_building` | 16×17×11 | Multi-storey apartment block |
| `modern_mansion` | 57×22×30 | Large mansion |
| `modern_minimalist` | 18×6×16 | Minimalist single-storey house |
| `house` | 68×26×70 | Spacious house with a yard |
| `home_with_pool` | 61×23×61 | House with a pool |
| `pool_slide_house` | 24×16×21 | House with a pool and a slide |
| `modern_villa_schem` | 43×23×38 | Modern villa |
| `library` | 17×12×17 | Stone library |

Blocks from newer Minecraft snapshots (shelves, copper chests, copper-golem statues, etc.) that do not exist in 1.21.4 are automatically skipped during conversion - the builds stay vanilla and load without errors.

### Procedurally generated buildings
---
| Structure | Size | Description |
|---|---|---|
| `modern_villa` | 18×7×18 | Modern villa with large windows |
| `modern_apartment` | 14×17×10 | Multi-storey residential building |
| `modern_office` | 12×32×12 | Tall office building |
| `modern_showroom` | 16×8×12 | Showroom with a display window |
| `modern_mall` | 22×10×16 | Shopping pavilion with balconies, an atrium and a glowing roof |

## Technical details
---
- Generation: `minecraft:jigsaw` + `template_pool` (single structure, no nested pools).
- Biome mapping via tag `#moremodernstructures:has_structure/modern_structures`.
- Structure format: vanilla NBT (`size`, `palette`, `blocks`).
- Datapack version: `pack_format 61` (Minecraft 1.21.4).
- Spawn point: `WORLD_SURFACE_WG`, start height `absolute 0`.
- Localization: English (`en_us`) and Russian (`ru_ru`).

## Compatibility
---
The mod was tested in the **Fabulously Optimized 1.21.4** modpack and does not modify any vanilla mechanics, recipes or items - it only registers world generation data. [Fabric API](https://modrinth.com/mod/fabric-api) is required.

## License and Authorship
---
- Procedural structures, schematic converter (`tools/SchemaTools.java`), and worldgen configuration — **byMr712**, licensed under the [Apache License 2.0](LICENSE).
- Towers based on the **Wild Cities** datapack — [source](https://modrinth.com/datapack/wild-cities) (MIT).
- **Cat Base.litematic** schematic — distributed by the creator **levitqte** on YouTube [source](https://youtu.be/tWjiTXby7LM?si=XxSnUFOKAVibPi_w).
- Schematics from the **Imported Schematics** section — builds by creators on [PlanetMinecraft](https://www.planetminecraft.com/).