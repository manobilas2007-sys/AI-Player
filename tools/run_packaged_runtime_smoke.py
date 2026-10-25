"""Boot the remapped mod in an isolated, production Fabric 1.20.1 server.

No development classpath or separate Baritone JAR is added. This is a JVM/Fabric
packaging test; it does not claim to execute Android/Fold Craft Launcher.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request


def download(url: str, destination: Path) -> None:
    if destination.exists():
        return
    request = urllib.request.Request(url, headers={"User-Agent": "AI-Player-runtime-smoke"})
    temporary = destination.with_suffix(destination.suffix + ".download")
    try:
        with urllib.request.urlopen(request, timeout=90) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mod-jar", type=Path, required=True)
    parser.add_argument("--smoke-jar", type=Path, required=True)
    parser.add_argument("--fallingtree-jar", type=Path, required=True)
    parser.add_argument("--fabric-api-jar", type=Path)
    parser.add_argument("--loader", default="0.20.0")
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    inputs = {"ai-player-under-test.jar": args.mod_jar.resolve(),
              "aiplayer-runtime-smoke.jar": args.smoke_jar.resolve(),
              "fallingtree.jar": args.fallingtree_jar.resolve()}
    for path in inputs.values():
        if not path.is_file():
            parser.error(f"Missing input JAR: {path}")
    if args.fabric_api_jar and not args.fabric_api_jar.is_file():
        parser.error(f"Missing Fabric API JAR: {args.fabric_api_jar}")
    if not args.loader or any(c not in "0123456789.-abcdefghijklmnopqrstuvwxyz" for c in args.loader):
        parser.error("Invalid Fabric Loader version")
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    mods = work / "mods"
    mods.mkdir(exist_ok=True)
    expected = set(inputs) | {"fabric-api.jar"}
    unexpected = {p.name for p in mods.glob("*.jar")} - expected
    if unexpected:
        parser.error(f"Smoke directory contains unexpected mods: {sorted(unexpected)}")
    for name, source in inputs.items():
        shutil.copyfile(source, mods / name)
    if args.fabric_api_jar:
        shutil.copyfile(args.fabric_api_jar.resolve(), mods / "fabric-api.jar")
    else:
        download("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/"
                 "0.92.2+1.20.1/fabric-api-0.92.2+1.20.1.jar", mods / "fabric-api.jar")
    launcher = work / "fabric-server-launch.jar"
    download(f"https://meta.fabricmc.net/v2/versions/loader/1.20.1/{args.loader}/1.0.1/server/jar", launcher)
    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "level-name=smoke-world\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
        '"biome":"minecraft:plains"}\n'
        "spawn-protection=0\nview-distance=2\nsimulation-distance=5\n"
        "difficulty=normal\nmax-tick-time=60000\nenable-status=false\n",
        encoding="utf-8")
    success = work / "smoke-success.txt"
    failure = work / "smoke-failure.txt"
    success.unlink(missing_ok=True)
    failure.unlink(missing_ok=True)
    old_crashes = set((work / "crash-reports").glob("*.txt"))
    log = work / "server-output.log"
    command = [args.java, "-Xms256m", "-Xmx1536m", "-Djava.awt.headless=true",
               "-jar", str(launcher), "nogui"]
    print(f"Starting production Fabric {args.loader}, Minecraft 1.20.1; log: {log}", flush=True)
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=work, stdin=subprocess.DEVNULL,
                                   stdout=output, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            print(log.read_text(encoding="utf-8", errors="replace")[-18000:], file=sys.stderr)
            raise SystemExit(f"Production runtime smoke exceeded {args.timeout}s")
    crashes = set((work / "crash-reports").glob("*.txt")) - old_crashes
    if code != 0 or failure.exists() or not success.exists() or crashes:
        print(log.read_text(encoding="utf-8", errors="replace")[-18000:], file=sys.stderr)
        if failure.exists():
            print(failure.read_text(encoding="utf-8", errors="replace"), file=sys.stderr)
        raise SystemExit(f"Production runtime smoke failed: exit={code}, success={success.exists()}, crashes={len(crashes)}")
    result = success.read_text(encoding="utf-8")
    if "status=passed\n" not in result or f"fabric_loader={args.loader}\n" not in result:
        raise SystemExit(f"Unexpected success marker: {result}")
    print(result, end="")


if __name__ == "__main__":
    main()
