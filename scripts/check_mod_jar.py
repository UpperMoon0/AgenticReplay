"""Check the NeoForge distributable for the startup/packaging regressions."""
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile


def check(path):
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        assert "de/javagl/jgltf/impl/v2/GlTF.class" in names, "Camera export requires glTF implementations"
        metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
        assert [mod["modId"] for mod in metadata["mods"]] == ["replaymod"]
        assert metadata["mods"][0]["displayName"] == "AgenticReplay"
        assert "${" not in metadata["mods"][0]["version"]
        forbidden = ("net/minecraft/", "com/google/common/", "com/google/gson/", "io/netty/")
        assert not any(name.startswith(forbidden) for name in names), "Minecraft libraries must not be bundled"
        assert "org/lwjgl/system/MemoryUtil.class" not in names, "Core LWJGL must come from Minecraft"
        assert "fabric.mod.json" not in names
        assert "com/replaymod/core/ReForgedPlayModStub.class" not in names
        for mixin in metadata["mixins"]:
            config = json.loads(re.sub(r"//[^\n]*", "", archive.read(mixin["config"]).decode()))
            for group in ("mixins", "client", "server"):
                for name in config.get(group, []):
                    entry = (config["package"] + "." + name).replace(".", "/") + ".class"
                    assert entry in names, f"Missing configured mixin: {entry}"
        for name in ("AgentApiServer", "AgentReplayApi", "ActorFramePayload", "ActorFrameIndex", "ActorFrameSquashFilter"):
            assert f"com/replaymod/agent/{name}.class" in names
        assert "assets/viaversion/data/mapping-26.1.json" in names or any("26.1" in name for name in names)
    print(f"Verified {Path(path).name}: one mod identity, complete mixins, agent features and isolated libraries")


if __name__ == "__main__":
    for path in sys.argv[1:]:
        if not path.endswith("-sources.jar"):
            check(path)
