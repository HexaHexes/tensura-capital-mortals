# Tensura Capital: Mortals

Faction capitals for the mortal races, Humans, Elves and Dwarves, for use with
the Tensura Faction Spawn ecosystem.

## What it does

This datapack registers the capital cities for the mortal factions. Once a
capital is registered here, the spawn mod picks it up automatically: a player
who first picks (or reincarnates into) one of these races is placed at the
nearest registered capital to the world spawn.

The capitals are a plain datapack, needing no extra mods beyond the spawn mod
and its own requirements. Add this repository's `datapack/` folder (or a zip of
it) to your world's `datapacks/` folder or your server's datapack loading path,
then run `/reload` and the capitals register.

## Requirements

- Minecraft 1.21.1 with NeoForge
- Tensura Faction Spawn (and through it: Tensura: Reincarnated, ManasCore)

## Status

**All three mortal capitals are built and registered:**

- **Humans**: a gated stone-brick town (gate and plaza, streets, cottages, keep).
- **Elves**: an ancient-forest clearing around a giant tree, with mossy
  walkways, ground huts and treetop dwellings (generates in the forest family
  and Tensura's `ancient_forest`).
- **Dwarves**: a mountain gatehouse with the massif rising behind it and a
  carved tunnel exit (generates in the mountain biomes).

Templates are generated from committed code (`tools/gen_templates.py`), so the
cities are regeneratable and reviewable as text.

## Credits

- Built on [NeoForge](https://neoforged.net/).
- Uses the race tag system from ManasCore / Tensura: Reincarnated.

## License

GNU Lesser General Public License v2.1 - see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
