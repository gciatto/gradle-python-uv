"""Merges the pyproject.toml specification produced by the Gradle python-uv plugin into pyproject.toml.

The merge is conservative, so that pyproject.toml can be tracked on Git and updated by Renovate:
- keys that the specification does not mention are left untouched (comments and layout included);
- values that did not change are not rewritten, so their formatting is preserved;
- in dependency arrays, the specification decides which packages are listed, while the version
  specifier with the highest lower bound wins (so Renovate's bumps survive regeneration);
- [[tool.uv.index]] entries are merged by name.

Usage: python pyproject_sync.py --spec SPEC.json --pyproject pyproject.toml [--check]
Exit codes: 0 = up to date (or updated), 1 = out of sync (--check only), 2 = error.
"""

from __future__ import annotations

import argparse
import difflib
import json
import sys
from collections.abc import Mapping, MutableMapping
from pathlib import Path
from typing import Any

import tomlkit
from packaging.requirements import InvalidRequirement, Requirement
from packaging.utils import canonicalize_name
from packaging.version import InvalidVersion, Version

Path_ = tuple[str, ...]

PATHS_REQUIREMENTS: set[Path_] = {("project", "dependencies"), ("build-system", "requires")}
PARENTS_REQUIREMENTS: set[Path_] = {("project", "optional-dependencies"), ("dependency-groups",)}
PATHS_ARRAY_OF_TABLES: set[Path_] = {("tool", "uv", "index")}
PATHS_MULTILINE: set[Path_] = {("project", "authors"), ("project", "classifiers")}
PARENTS_INLINE: set[Path_] = {("tool", "poe", "tasks")}
PATHS_INLINE: set[Path_] = {("build-system", "requires")}
OPERATORS_LOWER_BOUND = {">=", "==", "~=", ">", "==="}


def is_requirement_array(path: Path_) -> bool:
    return path in PATHS_REQUIREMENTS or path[:-1] in PARENTS_REQUIREMENTS


def unwrap(value: Any) -> Any:
    return value.unwrap() if hasattr(value, "unwrap") else value


def parse_requirement(text: str) -> Requirement | None:
    try:
        return Requirement(text)
    except InvalidRequirement:
        return None


def lower_bound(requirement: Requirement) -> Version | None:
    bounds = []
    for specifier in requirement.specifier:
        if specifier.operator in OPERATORS_LOWER_BOUND and not specifier.version.endswith("*"):
            try:
                bounds.append(Version(specifier.version))
            except InvalidVersion:
                continue
    return max(bounds) if bounds else None


def choose_requirement(desired: str, existing: str) -> str:
    new, old = parse_requirement(desired), parse_requirement(existing)
    if new is None or old is None:
        return desired
    if not str(new.specifier):
        return existing
    bound_new, bound_old = lower_bound(new), lower_bound(old)
    if bound_old is not None and (bound_new is None or bound_old > bound_new):
        return existing
    return desired


def requirement_key(item: Any) -> str | None:
    if not isinstance(item, str):
        return None
    requirement = parse_requirement(item)
    return canonicalize_name(requirement.name) if requirement else item


def merge_requirements(desired: list[Any], existing: list[Any]) -> list[Any]:
    existing_by_name = {requirement_key(item): item for item in existing if isinstance(item, str)}
    return [
        choose_requirement(item, existing_by_name[requirement_key(item)])
        if requirement_key(item) in existing_by_name
        else item
        for item in desired
    ]


def merge_by_name(desired: list[Any], existing: list[Any]) -> list[Any]:
    names = {entry.get("name") for entry in desired if isinstance(entry, Mapping)}
    kept = [
        entry
        for entry in existing
        if not (isinstance(entry, Mapping) and entry.get("name") in names)
    ]
    return desired + kept


def to_inline_table(value: Mapping[str, Any]) -> Any:
    table = tomlkit.inline_table()
    table.update({key: to_item(item, ()) for key, item in value.items()})
    return table


def to_item(value: Any, path: Path_) -> Any:
    if isinstance(value, list):
        if path in PATHS_ARRAY_OF_TABLES:
            tables = tomlkit.aot()
            for entry in value:
                table = tomlkit.table()
                table.update(entry)
                tables.append(table)
            return tables
        array = tomlkit.array()
        for entry in value:
            array.append(to_inline_table(entry) if isinstance(entry, Mapping) else entry)
        if value and (
            path in PATHS_MULTILINE or (is_requirement_array(path) and path not in PATHS_INLINE)
        ):
            array.multiline(True)
        return array
    if isinstance(value, Mapping):
        return to_inline_table(value)
    return value


def is_super_table(value: Mapping[str, Any]) -> bool:
    return bool(value) and all(
        isinstance(item, Mapping)
        or (isinstance(item, list) and all(isinstance(e, Mapping) for e in item))
        for item in value.values()
    )


def merge(container: MutableMapping[str, Any], key: str, value: Any, parent: Path_) -> None:
    path = parent + (key,)
    if (
        isinstance(value, Mapping)
        and parent not in PARENTS_INLINE
        and path not in PATHS_ARRAY_OF_TABLES
    ):
        current = container.get(key)
        if not isinstance(current, MutableMapping):
            container[key] = tomlkit.table(is_super_table=is_super_table(value))
            current = container[key]
        for child_key, child_value in value.items():
            merge(current, child_key, child_value, path)
        return
    existing = unwrap(container.get(key)) if key in container else None
    if isinstance(value, list) and isinstance(existing, list):
        if is_requirement_array(path):
            value = merge_requirements(value, existing)
        elif path in PATHS_ARRAY_OF_TABLES:
            value = merge_by_name(value, existing)
    if key in container and existing == value:
        return
    container[key] = to_item(value, path)


def with_fallbacks(document: Any, specification: dict[str, Any]) -> dict[str, Any]:
    """Inserts fallback values (e.g. the initial version) only where pyproject.toml lacks them."""
    desired = dict(specification.get("document", {}))
    for section, values in specification.get("fallbacks", {}).items():
        current = unwrap(document.get(section, {}))
        missing = {key: value for key, value in values.items() if key not in current}
        if not missing:
            continue
        section_items = list(desired.get(section, {}).items())
        # place fallbacks right after the first key (e.g. version after name)
        desired[section] = dict(section_items[:1] + list(missing.items()) + section_items[1:])
    return desired


def synchronize(text: str, specification: dict[str, Any]) -> str:
    document = tomlkit.parse(text)
    for key, value in with_fallbacks(document, specification).items():
        merge(document, key, value, ())
    return tomlkit.dumps(document)


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--pyproject", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    options = parser.parse_args(arguments)

    specification = json.loads(options.spec.read_text(encoding="utf-8"))
    original = options.pyproject.read_text(encoding="utf-8") if options.pyproject.exists() else ""
    updated = synchronize(original, specification)
    if updated == original:
        print(f"{options.pyproject} is up to date")
        return 0
    if options.check:
        diff = difflib.unified_diff(
            original.splitlines(keepends=True),
            updated.splitlines(keepends=True),
            fromfile=f"{options.pyproject} (current)",
            tofile=f"{options.pyproject} (expected by Gradle)",
        )
        sys.stdout.writelines(diff)
        return 1
    options.pyproject.write_text(updated, encoding="utf-8")
    print(f"{options.pyproject} updated")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Exception as error:  # noqa: BLE001
        print(f"error: {error}", file=sys.stderr)
        sys.exit(2)
