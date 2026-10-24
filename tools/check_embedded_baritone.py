"""Verify that the distributable contains the NPC library and its LGPL sources."""
from pathlib import Path
from io import BytesIO
import json
import zipfile

root = Path(__file__).resolve().parents[1]
jars = [p for p in (root / "build/libs").glob("ai-player-*.jar") if not p.name.endswith("-sources.jar")]
assert len(jars) == 1, f"Expected one playable JAR: {jars}"
with zipfile.ZipFile(jars[0]) as mod:
    metadata = json.loads(mod.read("fabric.mod.json"))
    assert metadata["depends"]["minecraft"] == "1.20.1"
    assert "baritone" not in metadata["depends"], "External Baritone must not be required"
    libraries = []
    for entry in metadata.get("jars", []):
        with zipfile.ZipFile(BytesIO(mod.read(entry["file"]))) as library:
            if "dev/aiplayer/baritone/AStarPathFinder.class" not in library.namelist():
                continue
            libraries.append(entry["file"])
            nested = json.loads(library.read("fabric.mod.json"))
            assert nested["id"] == "aiplayer_baritone_core"
            assert not nested.get("mixins") and not nested.get("entrypoints")
            for notice in ("GPL-3.txt", "LGPL-3.txt", "NOTICE.md"):
                assert f"META-INF/licenses/baritone/{notice}" in library.namelist()
                assert f"META-INF/licenses/baritone/{notice}" in mod.namelist()
            for name in library.namelist():
                if name.endswith(".class"):
                    data = library.read(name)
                    assert b"net/minecraft/client" not in data, name
                    assert b"net/minecraft/class_310" not in data, name
    assert len(libraries) == 1, f"Expected one embedded NPC Baritone library: {libraries}"

sources = list((root / "embedded-baritone/build/libs").glob("*-sources.jar"))
assert len(sources) == 1, f"Missing corresponding library sources: {sources}"
with zipfile.ZipFile(sources[0]) as source:
    assert "dev/aiplayer/baritone/AStarPathFinder.java" in source.namelist()
assert list((root / "build/libs").glob("*-sources.jar")), "Missing AI-Player sources"
print(f"Embedded Baritone, Minecraft 1.20.1, sources and licenses verified: {jars[0].name}")
