> **Language:** Русский · [English](README.en.md)

# More Modern Structures (Minecraft 1.21.4)

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-blue.svg)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)
![ModMenu](https://img.shields.io/badge/ModMenu-Supported-blue.svg)
![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)

Мод добавляет в мир процедурную генерацию ультрасовременных зданий, вилл, небоскрёбов и сооружений для **Minecraft 1.21.4 (Fabric)**. В постройках используются только ванильные блоки, поэтому мод полностью совместим с другими модами и не требует сторонних библиотек.

---

## Контент мода

Добавляемые структуры генерируются как отдельные `structure_set` (jigsaw, `random_spread`, расстояния 45 / 25 блоков) в биомах: равнины, саванны, пустыни, леса, тайга, холмы, горы, грибные поля и др.

### Историческое здание «Cat Base»

Современная база-убежище размером 22×17×27, перенесённая из схемы `Cat Base.litematic`. Конвертирована автоматически для полной совместимости с 1.21.4.

### Небоскрёбы из датапака Wild Cities

Четыре башни, декорированные на основе пака **Wild Cities**. Собраны из jigsaw-частей `base`, `middle` и `roof` в единые цельные структуры (высота башен: 24, 24, 33 и 17 блоков при плане 16×16).

### Импортированные схематики

Постройки, импортированные из схематиков и сконвертированные в ванильные структуры:

| Структура | Размеры | Описание |
|---|---|---|
| `pale_oak_house` | 21×11×20 | Современный дом из бледного дуба |
| `apartment_building` | 16×17×11 | Многоэтажный дом с квартирами |
| `modern_mansion` | 57×22×30 | Большой особняк |
| `modern_minimalist` | 18×6×16 | Минималистичный одноэтажный дом |
| `house` | 68×26×70 | Просторный дом с участком |
| `home_with_pool` | 61×23×61 | Дом с бассейном |
| `pool_slide_house` | 24×16×21 | Дом с бассейном и горкой |
| `modern_villa_schem` | 43×23×38 | Современная вилла |
| `library` | 17×12×17 | Библиотека из камня |

### Процедурно сгенерированные здания

| Структура | Размеры | Описание |
|---|---|---|
| `modern_villa` | 18×7×18 | Современная вилла с большими окнами |
| `modern_apartment` | 14×17×10 | Многоэтажный жилой дом |
| `modern_office` | 12×32×12 | Высокое офисное здание |
| `modern_showroom` | 16×8×12 | Шоурум с выставочной витриной |
| `modern_mall` | 22×10×16 | Торговый павильон с балконами, атриумом и светящейся крышей |

---

## Технические детали

- Генерация: `minecraft:jigsaw` + `template_pool` (единая структура без вложенных пулов).
- Привязка к биомам: тег `#moremodernstructures:has_structure/modern_structures`.
- Формат структур: ванильный NBT (`size`, `palette`, `blocks`).
- Версия датапака: `pack_format 61` (Minecraft 1.21.4).
- Локализация: английская (`en_us`) и русская (`ru_ru`).

---

## Установка

1. Скачайте последнюю версию со страницы [GitHub Releases](https://github.com/byMr712/MoreModernStructures-1.21.4-MinecraftMod/releases).
2. Требуются:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
3. Поместите `.jar` файл в папку `mods`.
4. Запустите игру.

---

## Сборка

1. Требуется Java 21 и Fabric Loader для Minecraft 1.21.4.
2. Для сборки выполните:
   ```bash
   ./gradlew build
   ```
3. Собранный файл находится в `build/libs/MoreModernStructures-1.21.4-byMr712.jar`.

---

## Авторы и лицензия

- Процедурные структуры, сборка, конвертер и конфигурация worldgen: **byMr712**, лицензия [Apache License 2.0](LICENSE).
- Башни на основе датапака **Wild Cities** (MIT).
- Схема **Cat Base.litematic**: автор **levitqte**.
- Схематики из раздела «Импортированные схематики»: авторы на [PlanetMinecraft](https://www.planetminecraft.com/).