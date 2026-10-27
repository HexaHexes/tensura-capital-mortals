#!/usr/bin/env python3
"""Datapack validator for the tensura-capital-* repositories.

Pure stdlib. Exit 0 = valid, 1 = problems found. Checks:

1. Every ``.json`` under ``datapack/`` parses.
2. Every in-repo ``worldgen/structure`` definition is referenced by at least one
   in-repo ``worldgen/structure_set`` - a structure outside any set can never
   generate and ``findNearestMapStructure`` will never find it.
3. Every structure_set reference resolves to an in-repo structure or an
   explicitly external namespace (``tensura``, ``minecraft``).
4. Every jigsaw ``template_pool`` element resolves to an in-repo structure
   template NBT or an external namespace.
5. Tag files are additive (``replace: true`` is forbidden - the capital tags are
   a shared contract) and values referencing this repo's own namespace exist
   in-repo (vanilla silently drops unresolvable tag values).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DATAPACK = REPO_ROOT / "datapack"
OWN_NAMESPACE = "tensura_faction_spawn"
EXTERNAL_NAMESPACES = {"minecraft", "tensura"}

errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def main() -> int:
    if not DATAPACK.is_dir():
        fail(f"missing datapack folder: {DATAPACK}")
        report()
        return 1

    documents: dict[Path, dict] = {}
    for path in sorted(DATAPACK.rglob("*.json")):
        try:
            documents[path] = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            fail(f"{path.relative_to(REPO_ROOT)}: invalid JSON ({exc})")

    structures = structure_ids(documents)
    referenced = structure_ids_referenced_by_sets(documents)

    for structure_id, path in sorted(structures.items()):
        if structure_id not in referenced:
            fail(f"{path.relative_to(REPO_ROOT)}: structure '{structure_id}' is not "
                 "referenced by any structure_set - it can never generate or be found")

    check_set_references(documents, structures)
    check_template_pools(documents)
    check_tags(documents, structures)
    report()
    return 1 if errors else 0


def datapack_relative(path: Path) -> str:
    return path.relative_to(DATAPACK).as_posix()


def structure_ids(documents: dict[Path, dict]) -> dict[str, Path]:
    """Map 'namespace:path' -> source path for every in-repo structure definition."""
    ids: dict[str, Path] = {}
    for path, data in documents.items():
        rel = datapack_relative(path)
        if "/tags/" in rel:
            continue  # tag files live under tags/worldgen/structure/ too - they are not definitions
        if "/worldgen/structure/" in rel and "/worldgen/structure_set/" not in rel:
            namespace = rel.split("/")[1]
            location = rel.split("/worldgen/structure/")[1].removesuffix(".json")
            ids[f"{namespace}:{location}"] = path
    return ids


def structure_ids_referenced_by_sets(documents: dict[Path, dict]) -> set[str]:
    referenced: set[str] = set()
    for path, data in documents.items():
        rel = datapack_relative(path)
        if "/tags/" in rel or "/worldgen/structure_set/" not in rel:
            continue
        for entry in data.get("structures", []):
            structure_id = entry.get("structure")
            if structure_id:
                referenced.add(structure_id)
    return referenced


def check_set_references(documents: dict[Path, dict], structures: dict[str, Path]) -> None:
    for path, data in documents.items():
        rel = datapack_relative(path)
        if "/tags/" in rel or "/worldgen/structure_set/" not in rel:
            continue
        for entry in data.get("structures", []):
            structure_id = entry.get("structure")
            if not structure_id:
                fail(f"{path.relative_to(REPO_ROOT)}: structure_set entry without a 'structure' id")
                continue
            namespace = structure_id.split(":")[0]
            if structure_id not in structures and namespace not in EXTERNAL_NAMESPACES:
                fail(f"{path.relative_to(REPO_ROOT)}: references unknown structure '{structure_id}'")


def check_template_pools(documents: dict[Path, dict]) -> None:
    for path, data in documents.items():
        rel = datapack_relative(path)
        if "/tags/" in rel or "/worldgen/template_pool/" not in rel:
            continue
        for index, element in enumerate(data.get("elements", [])):
            location = element.get("element", {}).get("location", "")
            if not location:
                continue
            namespace, _, template_path = location.partition(":")
            template = DATAPACK / "data" / namespace / "structures" / f"{template_path}.nbt"
            if not template.exists() and namespace not in EXTERNAL_NAMESPACES:
                fail(f"{path.relative_to(REPO_ROOT)}: element {index} references missing "
                     f"template '{location}' (expected {template.relative_to(REPO_ROOT)})")


def check_tags(documents: dict[Path, dict], structures: dict[str, Path]) -> None:
    for path, data in documents.items():
        rel = datapack_relative(path)
        if "/tags/" not in rel:
            continue
        if data.get("replace") is True:
            fail(f"{path.relative_to(REPO_ROOT)}: 'replace': true is forbidden - "
                 "capital tags are a shared additive contract")
        for value in data.get("values", []):
            if not isinstance(value, str) or value.startswith("#"):
                continue
            namespace = value.split(":")[0]
            if namespace == OWN_NAMESPACE and "/worldgen/structure/" in rel:
                if value not in structures and value not in structure_ids_referenced_by_sets(documents):
                    fail(f"{path.relative_to(REPO_ROOT)}: tag references '{value}' "
                         "which is not defined in this repo (vanilla would drop it silently)")


def report() -> None:
    if errors:
        print(f"VALIDATION FAILED: {len(errors)} problem(s)")
        for error in errors:
            print(f"  - {error}")
    else:
        print("VALIDATION OK")


if __name__ == "__main__":
    sys.exit(main())
