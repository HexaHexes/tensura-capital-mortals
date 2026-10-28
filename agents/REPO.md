# Repo rules — Tensura Capital: Mortals

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim — **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/tensura-capital-mortals` · **Type:** datapack (no Java) · **Branch:** `main`
**Platform:** Minecraft 1.21.1 datapack (`pack_format` 48) · **Artefact:** the `datapack/` folder (zipped for distribution)

Faction capitals for the mortal races - Humans, Elves, Dwarves. The datapack
appends its city structures to the central `#tensura_faction_spawn:capital/*`
tags owned by `tensura-faction-spawn`; that mod never needs to know the
structure ids, and this repo never edits its rules.

**Dependencies**
- Required: `tensura-faction-spawn` (owns the central capital tags and the
  arrival behaviour), and through it Tensura: Reincarnated + ManasCore.

**Test:** `python tools/validate.py` - the only gate. CI runs it as the
`validate` check; there is no Java, no Gradle build and no GameTest in this
repository, so the mod template's junit/gametest jobs are replaced by the
validator.

**Gotchas**
- **The capital tags are a shared additive contract.** `"replace": true` is
  forbidden (the validator enforces it) - every consumer appends.
- **Capital IDs are permanent.** Once a city ships, its id, arrival point and
  tag wiring are part of the ownership/lease future (spec §18.3/§18.4) - a
  rename is a migration, not a refactor.
- **Every structure needs a structure_set.** `findNearestMapStructure` only
  finds structures that belong to one; the validator enforces the pairing.
- Jigsaw pool templates are NBT under `datapack/data/<ns>/structure/`
  (singular - renamed from `structures/` in 1.21) - the validator resolves
  every pool element reference against those files, and silently-missing
  templates would break generation at runtime.
- `gradle.properties` exists only to feed the version-bump workflow
  (`mod_version`); there is no build.
- Worldgen placement is biome-constrained - a "find the city" test is
  environmental. Wiring (tags -> registered structures) is what the validator
  can prove headlessly.

## CI & release model - this repo

- `ci.yml` runs the `validate` check (`python tools/validate.py`) instead of the
  template's junit/gametest jobs - there is no Java here. The template's three-check
  model applies to this repo as its one check.
- `version-bump.yml` ships; `gradle.properties` exists only to feed it (`mod_version`).
- `release.yml`, `publish.yml` and `modrinth.yml` do not ship here: there is no
  Gradle build to run or publish - the artefact is the zipped `datapack/` folder.
  A release flow for this repo would be a datapack-specific variant (zip the
  folder, attach it to the release, then the marketplace workflows could upload
  it) - a deliberate future decision, not a silent omission.