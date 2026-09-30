> **Language:** [Русский](README.md) · English

# More Modern Structures (Minecraft 1.21.4)

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-blue.svg)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)
![ModMenu](https://img.shields.io/badge/ModMenu-Supported-blue.svg)
![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)

A mod that adds procedurally generated modern architecture, villas, skyscrapers, and urban buildings to **Minecraft 1.21.4 (Fabric)**. Built exclusively using vanilla blocks for complete modpack compatibility without external library dependencies.

---

## Mod Content

Structures generate across various biomes (plains, savannas, deserts, forests, taigas, hills, mountains, mushroom fields) via individual `structure_set` configurations (jigsaw, `random_spread`, 45 / 25 spacing).

### Cat Base Landmark

A modern survival base measuring 22×17×27, converted from the `Cat Base.litematic` schematic for full 1.21.4 compatibility.

### Skyscrapers from Wild Cities

Four towers adapted from the **Wild Cities** datapack, assembled from jigsaw `base`, `middle`, and `roof` components into full monolithic towers (heights: 24, 24, 33, and 17 blocks on 16×16 footprints).

### Imported Schematics

Community structures converted into vanilla NBT structure files:

| Structure | Dimensions | Description |
|---|---|---|
| `pale_oak_house` | 21×11×20 | Modern Pale Oak residence |
| `apartment_building` | 16×17×11 | Multi-story apartment block |
| `modern_mansion` | 57×22×30 | Expansive luxury mansion |
| `modern_minimalist` | 18×6×16 | Single-story minimalist house |
| `house` | 68×26×70 | Spacious villa with yard |
| `home_with_pool` | 61×23×61 | Modern residence with pool |
| `pool_slide_house` | 24×16×21 | House with pool and slide |
| `modern_villa_schem` | 43×23×38 | Contemporary luxury villa |
| `library` | 17×12×17 | Stone city library |

### Procedurally Generated Buildings

| Structure | Dimensions | Description |
|---|---|---|
| `modern_villa` | 18×7×18 | Contemporary villa with panoramic windows |
| `modern_apartment` | 14×17×10 | Urban apartment complex |
| `modern_office` | 12×32×12 | High-rise corporate office |
| `modern_showroom` | 16×8×12 | Commercial showroom with display windows |
| `modern_mall` | 22×10×16 | Modern shopping mall with atrium and glass roof |

---

## Technical Details

- Generation: `minecraft:jigsaw` + `template_pool`.
- Biome mapping tag: `#moremodernstructures:has_structure/modern_structures`.
- Structure format: Vanilla NBT (`size`, `palette`, `blocks`).
- Datapack version: `pack_format 61` (Minecraft 1.21.4).
- Full localization: English (`en_us`) and Russian (`ru_ru`).

---

## Installation

1. Download the latest release from [GitHub Releases](https://github.com/byMr712/MoreModernStructures-1.21.4-MinecraftMod/releases).
2. Requires:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the `.jar` file into your `mods` folder.
4. Launch the game.

---

## Building

1. Requires Java 21 and Fabric Loader for Minecraft 1.21.4.
2. To build the project, run:
   ```bash
   ./gradlew build
   ```
3. The built jar file will be located at `build/libs/MoreModernStructures-1.21.4-byMr712.jar`.

---

## Credits & License

- Procedural structures, converter tools, and worldgen configuration: **byMr712**, licensed under [Apache License 2.0](LICENSE).
- Towers adapted from **Wild Cities** datapack (MIT).
- **Cat Base.litematic** schematic by **levitqte**.
- Schematics in the "Imported Schematics" section by creators on [PlanetMinecraft](https://www.planetminecraft.com/).