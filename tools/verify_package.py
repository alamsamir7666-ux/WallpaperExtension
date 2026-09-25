#!/usr/bin/env python3
"""End-to-end verification of the built extension package.

This script re-runs, offline, every check the host app's extension engine
performs at install and load time, plus the release-dex ABI audit the host
applies to bundled plugins (tools/audit_release_dex.py in the Cloudimage
repo). Sources of truth, all read from the host v1.0.5 tree:

  1. ExtensionPackages.readManifestText — a package is a zip with
     `extension.json` at the root next to the payload;
  2. ExtensionManifest.parse/validate — exact schema, JSON configuration
     (ignoreUnknownKeys, coerceInputValues), ID/entryClass patterns and
     field constraints;
  3. ProviderApi.isSupported — the API version gate (exact match, V1 = 1);
  4. ExtensionLoader — entryClass must resolve inside the payload dex and
     implement the provider contract (the JVM test suite proves the class
     side; this script proves the dex side);
  5. proguard-rules.pro — the host's release build keeps, under ORIGINAL
     names: com.cloudimage.provider.api.**, kotlin.**,
     kotlinx.serialization.**. Everything else an Android runtime provides
     comes from the bootclasspath prefixes the host's own audit accepts.

The plugin dex therefore must:
  - define ONLY classes under com/cloudimage/wallpaperflare/ (the vendored
    contract must never leak into the payload), and
  - reference NOTHING outside the kept surface and the bootclasspath.

Usage:
    python3 tools/verify_package.py <package.zip> [<expected-entry-class>]

Exit 0 = the package would survive the host engine; 1 = it would not.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

# --- Host constants (ported verbatim) -------------------------------------

# ExtensionManifest.ID_PATTERN — reverse-DNS, lower-case, >= 2 segments.
ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")

# ExtensionManifest.ENTRY_CLASS_PATTERN — fully-qualified class name.
ENTRY_CLASS_PATTERN = re.compile(r"^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+$")

# ProviderApi.VERSION — the exact-match install gate.
SUPPORTED_API_VERSION = 1

# tools/audit_release_dex.py BOOTCLASSPATH_PREFIXES — types the Android
# runtime itself provides; never required inside the host dex.
BOOTCLASSPATH_PREFIXES = (
    "Ljava/",
    "Landroid/",
    "Ljavax/",
    "Lorg/w3c/dom/",
    "Lorg/xml/",
    "Ldalvik/",
)

# proguard-rules.pro -keep rules for the plugin runtime ABI, expressed as
# dex descriptor prefixes. com.cloudimage.provider.api.** { *; },
# kotlin.** { *; }, kotlinx.serialization.** { *; }.
KEPT_PREFIXES = (
    "Lcom/cloudimage/provider/api/",
    "Lkotlin/",
    "Lkotlinx/serialization/",
)

# This plugin's own namespace — the only classes the payload may define.
PLUGIN_PREFIX = "Lcom/cloudimage/wallpaperflare/"

DEFAULT_ENTRY_CLASS = "com.cloudimage.wallpaperflare.WallpaperflareWallpaperProvider"


class VerificationError(Exception):
    """One rule the host engine would enforce, violated."""


# --- Manifest: ExtensionManifest.parse + validate, in Python --------------

def parse_manifest(text: str) -> dict:
    """Decodes and validates extension.json exactly like the host does.

    Mirrors kotlinx.serialization with ignoreUnknownKeys + coerceInputValues
    over the ExtensionManifest schema: unknown keys are dropped, nulls
    coerce to defaults where a default exists, missing required fields and
    constraint violations raise with a readable message.
    """
    try:
        raw = json.loads(text)
    except json.JSONDecodeError as e:
        raise VerificationError(f"manifest is not valid JSON: {e.msg}")

    if not isinstance(raw, dict):
        raise VerificationError("manifest must be a JSON object")

    defaults = {"author": "", "description": ""}
    manifest: dict = {}
    for key, value in raw.items():
        if key in defaults and value is None:  # coerceInputValues
            value = defaults[key]
        manifest[key] = value

    for key in ("id", "name", "versionName", "versionCode", "apiVersion", "entryClass"):
        if key not in manifest or manifest[key] is None:
            raise VerificationError(f"manifest is missing required field '{key}'")
    for key, default in defaults.items():
        manifest.setdefault(key, default)

    for key in ("versionCode", "apiVersion"):
        value = manifest[key]
        if isinstance(value, bool) or not isinstance(value, int):
            if isinstance(value, str) and value.isdigit():
                manifest[key] = int(value)  # coerceInputValues-style leniency
            else:
                raise VerificationError(f"manifest field '{key}' must be an integer, was {value!r}")

    return validate_manifest(manifest)


def validate_manifest(manifest: dict) -> dict:
    """ExtensionManifest.validate(): field constraints, first failure wins."""
    if not ID_PATTERN.match(manifest["id"]):
        raise VerificationError(
            "id must be lower-case reverse-DNS with at least two segments, "
            f"was '{manifest['id']}'"
        )
    if not str(manifest["name"]).strip():
        raise VerificationError("name must not be blank")
    if not str(manifest["versionName"]).strip():
        raise VerificationError("versionName must not be blank")
    if manifest["versionCode"] < 1:
        raise VerificationError(f"versionCode must be >= 1, was {manifest['versionCode']}")
    if manifest["apiVersion"] < 1:
        raise VerificationError(f"apiVersion must be >= 1, was {manifest['apiVersion']}")
    if not ENTRY_CLASS_PATTERN.match(manifest["entryClass"]):
        raise VerificationError(
            f"entryClass must be a fully-qualified class name, was '{manifest['entryClass']}'"
        )
    return manifest


# --- Minimal dex reader: strings, types, class definitions ----------------

def read_uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, offset
        shift += 7


def dex_type_table(dex: bytes) -> list[str]:
    """Every type the dex references, in type_ids order (descriptor form).

    The type_ids table is the complete closure of type references — field
    types, method prototypes, annotations — so anything the payload could
    ever ask a classloader to resolve appears here.
    """
    string_ids_size = int.from_bytes(dex[56:60], "little")
    string_ids_off = int.from_bytes(dex[60:64], "little")
    strings = []
    for i in range(string_ids_size):
        off = int.from_bytes(dex[string_ids_off + 4 * i : string_ids_off + 4 * i + 4], "little")
        _length, pos = read_uleb128(dex, off)
        end = dex.index(b"\x00", pos)
        strings.append(dex[pos:end].decode("utf-8", errors="replace"))

    type_ids_size = int.from_bytes(dex[64:68], "little")
    type_ids_off = int.from_bytes(dex[68:72], "little")
    type_names = []
    for i in range(type_ids_size):
        string_idx = int.from_bytes(dex[type_ids_off + 4 * i : type_ids_off + 4 * i + 4], "little")
        type_names.append(strings[string_idx])
    return type_names


def dex_defined_classes(dex: bytes) -> list[str]:
    """Descriptors of every class this dex DEFINES (not merely references)."""
    type_ids_size = int.from_bytes(dex[64:68], "little")
    type_ids_off = int.from_bytes(dex[68:72], "little")
    class_defs_size = int.from_bytes(dex[96:100], "little")
    class_defs_off = int.from_bytes(dex[100:104], "little")

    if class_defs_size == 0:
        return []

    # Resolve only the strings the type table needs, once.
    string_ids_off = int.from_bytes(dex[60:64], "little")
    string_cache: dict[int, str] = {}
    type_idx_to_name: dict[int, str] = {}
    for i in range(type_ids_size):
        string_idx = int.from_bytes(dex[type_ids_off + 4 * i : type_ids_off + 4 * i + 4], "little")
        if string_idx not in string_cache:
            off = int.from_bytes(dex[string_ids_off + 4 * string_idx : string_ids_off + 4 * string_idx + 4], "little")
            _length, pos = read_uleb128(dex, off)
            end = dex.index(b"\x00", pos)
            string_cache[string_idx] = dex[pos:end].decode("utf-8", errors="replace")
        type_idx_to_name[i] = string_cache[string_idx]

    defined = []
    for i in range(class_defs_size):
        type_idx = int.from_bytes(dex[class_defs_off + 32 * i : class_defs_off + 32 * i + 4], "little")
        defined.append(type_idx_to_name[type_idx])
    return defined


def resolvable_type(descriptor: str, defined_set: set[str]) -> bool:
    """True when a classloader could resolve [descriptor] on the host.

    Primitive descriptors (`V`, `I`, …) are not classes; array markers are
    stripped so `[Lkotlin/Pair;` resolves iff `kotlin.Pair` does — the same
    normalization the host's dexdump-based audit achieves by matching only
    descriptors that contain a package slash. A type the payload itself
    defines (arrays of included classes) always resolves.
    """
    bare = descriptor.lstrip("[")
    if not bare.startswith("L") or not bare.endswith(";"):
        return True  # primitive (or malformed-but-inert) — not a class reference
    if bare in defined_set:
        return True  # defined by this very payload
    return bare.startswith(BOOTCLASSPATH_PREFIXES) or bare.startswith(KEPT_PREFIXES)


# --- The verification chain ------------------------------------------------

def verify(package: Path, expected_entry_class: str) -> dict:
    sha256 = hashlib.sha256(package.read_bytes()).hexdigest()
    size_bytes = package.stat().st_size

    with zipfile.ZipFile(package) as archive:
        names = set(archive.namelist())
        if names != {"extension.json", "classes.dex"}:
            raise VerificationError(
                "package must contain exactly extension.json + classes.dex "
                f"(ExtensionPackages/ExtensionInstaller contract), was {sorted(names)}"
            )
        manifest_text = archive.read("extension.json").decode("utf-8")
        dex = archive.read("classes.dex")

    manifest = parse_manifest(manifest_text)

    # ProviderApi.isSupported — exact match, older AND newer both refused.
    if manifest["apiVersion"] != SUPPORTED_API_VERSION:
        raise VerificationError(
            f"apiVersion {manifest['apiVersion']} is not supported by this host "
            f"(exact match on {SUPPORTED_API_VERSION} required)"
        )

    # The package file name must be <id>.zip — that is what the installer
    # writes and what the repository index advertises as fileName.
    if package.stem != manifest["id"]:
        raise VerificationError(
            f"package file name must be '<id>.zip', was '{package.name}' for id '{manifest['id']}'"
        )

    defined = dex_defined_classes(dex)
    referenced = set(dex_type_table(dex))
    defined_set = set(defined)

    foreign = sorted(c for c in defined_set if not c.startswith(PLUGIN_PREFIX))
    if foreign:
        raise VerificationError(
            "payload dex defines classes outside the plugin namespace "
            f"(the vendored contract must stay out): {foreign}"
        )

    entry_descriptor = "L" + expected_entry_class.replace(".", "/") + ";"
    if entry_descriptor not in defined_set:
        raise VerificationError(
            f"entryClass {expected_entry_class} is not defined by the payload dex — "
            "ExtensionLoader would fail with EntryClassMissing"
        )
    if manifest["entryClass"] != expected_entry_class:
        raise VerificationError(
            f"manifest entryClass {manifest['entryClass']} disagrees with the expected "
            f"{expected_entry_class}"
        )

    external = referenced - defined_set
    unresolvable = [t for t in sorted(external) if not resolvable_type(t, defined_set)]
    if unresolvable:
        raise VerificationError(
            "payload dex references types outside the host-guaranteed surface "
            "(kept: provider.api/kotlin/kotlinx.serialization + bootclasspath): "
            f"{unresolvable}"
        )

    return {
        "manifest": manifest,
        "sha256": sha256,
        "sizeBytes": size_bytes,
        "definedClasses": len(defined),
        "externalReferences": [t for t in sorted(external) if t.lstrip("[").startswith("L")],
    }


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    package = Path(sys.argv[1])
    entry_class = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_ENTRY_CLASS
    if not package.is_file():
        print(f"FAIL: {package}: not a file")
        return 1

    try:
        report = verify(package, entry_class)
    except VerificationError as e:
        print(f"FAIL: {e}")
        return 1

    manifest = report["manifest"]
    print(f"OK: {package.name}")
    print(f"    id={manifest['id']} version={manifest['versionName']} ({manifest['versionCode']})")
    print(f"    apiVersion={manifest['apiVersion']} entryClass={manifest['entryClass']}")
    print(f"    sha256={report['sha256']}")
    print(f"    sizeBytes={report['sizeBytes']}")
    print(f"    dex defines {report['definedClasses']} classes, all under com/cloudimage/wallpaperflare/")
    ext = report["externalReferences"]
    if ext:
        print(f"    external references ({len(ext)}) all resolve against the kept surface:")
        for t in ext:
            print(f"      {t}")
    print("    package satisfies the host engine's install + load + release-ABI rules")
    return 0


if __name__ == "__main__":
    sys.exit(main())
