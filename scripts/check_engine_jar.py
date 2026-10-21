"""Validate the actual distributable for each maintained replay engine lane."""
import io
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

ENGINE = {
    'com/replaymod/core/ReplayMod.class',
    'com/replaymod/core/gui/GuiReplaySettings.class',
    'com/replaymod/recording/ReplayModRecording.class',
    'com/replaymod/replay/ReplayModReplay.class',
    'com/replaymod/simplepathing/ReplayModSimplePathing.class',
    'com/replaymod/render/ReplayModRender.class',
}
CAMERA_LIBS = {
    'de/javagl/jgltf/impl/v1/GlTF.class',
    'de/javagl/jgltf/impl/v2/GlTF.class',
    'de/javagl/jgltf/model/io/v2/GltfAssetWriterV2.class',
    'com/fasterxml/jackson/databind/ObjectMapper.class',
    'com/fasterxml/jackson/core/JsonFactory.class',
    'com/fasterxml/jackson/annotation/JsonProperty.class',
}


def all_entries(archive, depth=0):
    result = set(archive.namelist())
    if depth < 2:
        for name in archive.namelist():
            if name.endswith('.jar'):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as child:
                    result.update(all_entries(child, depth + 1))
    return result


def check(lane, path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), 'Duplicate ZIP entries'
        entries = all_entries(archive)
        manifest = re.sub(r'\r?\n ', '', archive.read('META-INF/MANIFEST.MF').decode())
        attributes = dict(line.split(': ', 1) for line in manifest.splitlines() if ': ' in line)
        assert ENGINE <= set(names), f'Missing in-tree replay engine/UI: {ENGINE - set(names)}'
        if lane == 'legacy-1.7.10':
            assert 'TweakClass' not in attributes and 'TweakOrder' not in attributes
            assert 'ModSide' not in attributes, 'Keep the GTNH JarJar-compatible launch profile'
            assert attributes['ForceLoadAsMod'] == 'true'
            assert attributes['FMLCorePluginContainsFMLMod'] == 'true'
            assert attributes['AgenticReplay-Launch-Profile'] == 'unimixins'
            assert attributes['AgenticReplay-Engine-Build'] == 'source-overlay-v1'
            assert attributes['FMLCorePlugin'] == 'com.replaymod.core.LoadingPlugin'
            assert not any((n.startswith('org/spongepowered/') and n.endswith('.class')) or n.startswith('META-INF/services/org.spongepowered.') for n in names), 'UniMixins must own the Mixin runtime'
            assert 'com/agenticreplay/legacy/AgenticReplayLegacy.class' in entries
            info = json.loads(archive.read('mcmod.info'))
            ids = [mod['modid'] for mod in info]
            assert len(ids) == len(set(ids)) and {'agenticreplay', 'replaymod'} <= set(ids)
            for name in ['META-INF/LICENSE-agenticreplay.txt', 'META-INF/LICENSE-agenticreplay-common.txt']:
                assert name in entries, name
            tracker = archive.read('com/replaymod/render/hooks/GLStateTracker.class')
            assert b'notify_glEnable' in tracker and b'notify_glDisable' in tracker
            seed = Path(__file__).resolve().parents[1] / 'legacy-1.7.10/engine-inputs/replaymod-1.7.10-2.5.2.jar'
            with zipfile.ZipFile(seed) as upstream:
                replacement_families = ('com/replaymod/core/asm/GLStateTrackerTransformer', 'com/replaymod/render/hooks/GLStateTracker', 'com/replaymod/core/LoadingPlugin')
                for name in upstream.namelist():
                    if name.endswith('/') or name in {'META-INF/MANIFEST.MF', 'mcmod.info', 'META-INF/services/javax.annotation.processing.Processor'} or name.startswith(replacement_families + ('META-INF/services/org.spongepowered.',)) or (name.startswith('org/spongepowered/') and name.endswith('.class')):
                        continue
                    assert archive.read(name) == upstream.read(name), f'Unexpected upstream change: {name}'
            for name in names:
                if name.endswith('.class') and name.startswith(('com/agenticreplay/', 'com/replaymod/agent/')):
                    assert int.from_bytes(archive.read(name)[6:8], 'big') == 52, f'Not Java 8: {name}'
        else:
            assert CAMERA_LIBS <= entries, f'Camera export runtime is incomplete: {CAMERA_LIBS - entries}'
            name = 'META-INF/mods.toml' if lane == 'forge-1.20.1' else 'META-INF/neoforge.mods.toml'
            info = tomllib.loads(archive.read(name).decode())
            # ReForgedPlay retains both its loader entry point and the ReplayMod
            # compatibility identity in the original upstream descriptor.
            identities = ['reforgedplaymod', 'replaymod'] if lane == 'neoforge-1.21.1' else ['replaymod']
            assert [mod['modId'] for mod in info['mods']] == identities
            assert info['mods'][0]['displayName'] == 'AgenticReplay'
            assert '${' not in info['mods'][0]['version']
            configs = [m['config'] for m in info.get('mixins', [])]
            if lane == 'forge-1.20.1':
                configs = attributes['MixinConfigs'].split(',')
            if not configs:
                configs = [n for n in names if n.startswith('mixins.') and n.endswith('.json')]
            assert configs
            for config in configs:
                data = json.loads(re.sub(r'//[^\n]*', '', archive.read(config).decode()))
                for group in ['client', 'server', 'mixins']:
                    for mixin in data.get(group, []):
                        if mixin is None:
                            continue
                        entry = (data['package'] + '.' + mixin).replace('.', '/') + '.class'
                        assert entry in entries, f'{config}: missing {entry}'
        assert not any(n.startswith('net/minecraft/') for n in names), 'Vanilla must not be redistributed'
    print(f'Verified {lane}: packaged replay engine, UI, discovery metadata and required runtime libraries')


if __name__ == '__main__':
    check(sys.argv[1], sys.argv[2])
