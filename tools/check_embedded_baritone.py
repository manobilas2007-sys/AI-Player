"""Verify and execute the LGPL classes from the playable JAR, without dev outputs.

No arguments uses Gradle build directories. Artifact/component paths are optional
positional arguments for checking the exact files delivered to a player.
"""
from __future__ import annotations

import argparse
from collections import Counter
from io import BytesIO
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CORE = "dev/aiplayer/baritone/"
LICENSES = "META-INF/licenses/baritone/"
REQUIRED = {"Goal", "Position", "Step", "Movement", "SearchContext", "SearchLimits",
            "SearchResult", "AStarPathFinder", "PathNode", "BinaryHeapOpenSet", "ActionCosts",
            "GoalBlock", "GoalNear", "GoalXZ", "GoalYLevel", "GoalComposite", "GoalRunAway"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def one_binary(directory: Path, pattern: str) -> Path:
    jars = sorted(p for p in directory.glob(pattern) if not p.name.endswith("-sources.jar"))
    require(len(jars) == 1, f"Expected one binary JAR in {directory}: {jars}")
    return jars[0]


def check_artifact(artifact: Path, component: Path) -> set[str]:
    with zipfile.ZipFile(component) as library, zipfile.ZipFile(artifact) as mod:
        metadata = json.loads(mod.read("fabric.mod.json"))
        require(metadata["depends"]["minecraft"] == "1.20.1", "Minecraft must remain 1.20.1")
        for external in ("baritone", "aiplayer_baritone_core"):
            require(external not in metadata["depends"], f"External {external} must not be required")
        names = mod.namelist()
        duplicates = [name for name, count in Counter(names).items() if count > 1 and name.endswith(".class")]
        require(not duplicates, f"Duplicate class entries in playable JAR: {duplicates}")
        classes = {name for name in library.namelist() if name.endswith(".class")}
        require(classes, "The separately built LGPL module contains no classes")
        require(all(name.startswith(CORE) for name in classes), "Unexpected non-core classes in LGPL module")
        for name in REQUIRED:
            require(CORE + name + ".class" in classes, f"Missing module class: {name}")
        shipped = {name for name in names if name.startswith(CORE) and name.endswith(".class")}
        missing, extra = sorted(classes - shipped), sorted(shipped - classes)
        require(not missing, "Core classes missing from playable JAR root (nested metadata is insufficient): "
                + ", ".join(missing))
        require(not extra, f"Stale/unexpected core classes in playable JAR: {extra}")
        for name in sorted(classes):
            original, released = library.read(name), mod.read(name)
            require(original == released, f"Released class differs from LGPL module: {name}")
            require(released[:4] == b"\xca\xfe\xba\xbe", f"Invalid class file: {name}")
            require(int.from_bytes(released[6:8], "big") == 61, f"Class is not Java 17: {name}")
            require(b"net/minecraft/" not in released and b"net/fabricmc/" not in released,
                    f"Game/client dependency leaked into pure Java library: {name}")
        for name in names:
            if not name.endswith(".jar"):
                continue
            with zipfile.ZipFile(BytesIO(mod.read(name))) as nested:
                duplicate_core = sorted(classes.intersection(nested.namelist()))
                require(not duplicate_core, f"Core duplicated in nested {name}: {duplicate_core}")
                if "fabric.mod.json" in nested.namelist():
                    nested_metadata = json.loads(nested.read("fabric.mod.json"))
                    require(nested_metadata.get("id") != "aiplayer_baritone_core", "Old separately loaded core mod remains nested")
        for entry in metadata.get("jars", []):
            require(entry["file"] in names, f"Metadata references missing nested JAR: {entry}")
        notices = {name for name in library.namelist() if name.startswith(LICENSES) and not name.endswith("/")}
        for notice in ("GPL-3.txt", "LGPL-3.txt", "NOTICE.md", "UPSTREAM.md", "ADAPTATION.md"):
            require(LICENSES + notice in notices, f"Missing component notice: {notice}")
        for name in notices:
            require(name in names, f"Missing playable-JAR notice: {name}")
            require(mod.read(name) == library.read(name), f"Mismatched LGPL notice: {name}")
        return classes


def check_sources(artifact: Path, component: Path, classes: set[str],
                  app_sources: Path | None, component_sources: Path | None) -> None:
    app_sources = app_sources or artifact.with_name(artifact.stem + "-sources.jar")
    component_sources = component_sources or component.with_name(component.stem + "-sources.jar")
    require(app_sources.is_file(), f"Missing corresponding AI Player sources: {app_sources}")
    require(component_sources.is_file(), f"Missing corresponding LGPL sources: {component_sources}")
    with zipfile.ZipFile(app_sources) as source:
        require("dev/aiplayer/AIPlayer.java" in source.namelist(), "AI Player source JAR is incomplete")
    with zipfile.ZipFile(component_sources) as source:
        names = set(source.namelist())
        for name in classes:
            java_name = name[:-6].split("$", 1)[0] + ".java"
            require(java_name in names, f"Missing LGPL source for {name}")
        require("UPSTREAM.md" in names, "Component sources lack adaptation/provenance instructions")
        require(any(name.startswith("upstream/") and name.endswith("AStarPathFinder.java") for name in names),
                "Component sources lack the pinned upstream reference")


def java_tools(java_home: Path | None) -> tuple[str, str]:
    home = java_home or (Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    java = str(home / "bin/java") if home else shutil.which("java")
    javac = str(home / "bin/javac") if home else shutil.which("javac")
    require(bool(java and javac), "A Java 17 JDK is required for the packaged artifact smoke test")
    for executable in (java, javac):
        version = subprocess.run([executable, "-version"], text=True, capture_output=True, check=True)
        require(bool(re.search(r"\b17(?:[.\"\s]|$)", version.stdout + version.stderr)),
                f"Packaged smoke test must use Java 17: {executable}")
    return java, javac


def run_smoke(artifact: Path, java_home: Path | None) -> None:
    java, javac = java_tools(java_home)
    harness = ROOT / "src/packagedtests/PackagedBaritoneSmokeTest.java"
    require(harness.is_file(), f"Missing packaged-JAR test harness: {harness}")
    with tempfile.TemporaryDirectory(prefix="aiplayer-packaged-smoke-") as temporary:
        # Compile reflection-only harness against the JDK, never a module/dev classpath.
        subprocess.run([javac, "--release", "17", "-encoding", "UTF-8", "-d", temporary, str(harness)],
                       check=True, timeout=60)
        # Only harness is on the application classpath; it isolates the playable JAR itself.
        subprocess.run([java, "-cp", temporary, "PackagedBaritoneSmokeTest", str(artifact)],
                       check=True, timeout=60)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", nargs="?", type=Path)
    parser.add_argument("component", nargs="?", type=Path)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--app-sources", type=Path)
    parser.add_argument("--component-sources", type=Path)
    args = parser.parse_args()
    artifact = (args.artifact or one_binary(ROOT / "build/libs", "ai-player-*.jar")).resolve()
    component = (args.component or one_binary(ROOT / "embedded-baritone/build/libs", "*.jar")).resolve()
    require(artifact.is_file(), f"Missing playable JAR: {artifact}")
    require(component.is_file(), f"Missing LGPL component JAR: {component}")
    classes = check_artifact(artifact, component)
    check_sources(artifact, component, classes, args.app_sources, args.component_sources)
    run_smoke(artifact, args.java_home)
    print(f"Playable JAR verified: {len(classes)} byte-identical Java 17 classes, no nested duplicate, "
          f"LGPL notices/sources present, isolated execution passed: {artifact.name}")


if __name__ == "__main__":
    main()
