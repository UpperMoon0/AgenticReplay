# Runtime validation for the engine fork change

Builds and regressions are recorded separately from native client results.
This file must not imply that source adoption establishes rendering parity.

| Lane | Native validation for 0.8.0 |
| --- | --- |
| Forge 1.7.10 / GTNH 2.9.0-RC-2 | Startup passed in the isolated profile below: Forge loaded 325 mods, including both fork identities, and Angelica reached frame pacing. Recording/replay/export acceptance remains outstanding |
| Forge 1.20.1 | Not rerun for this change; source behavior is retained, with shared contracts/distribution checks run separately |
| NeoForge 1.21.1 | Native startup, recording/replay/path/export UI acceptance is outstanding; compile/package success does not certify it |
| NeoForge 26.1.2 | Not rerun for this change; source behavior is retained, with shared contracts/distribution checks run separately |

The legacy profile uses the rebuilt single fork JAR in place of both old
engine/adapter JARs. The original filming profile and its mod files are not
modified. A disposable loopback vanilla 1.7.10 server is the initial recording
fixture; that tests the GTNH client stack, not a GTNH server handshake or
modded-world content compatibility. Shader export and the known Angelica
clock/terrain/hand gaps remain unverified unless explicitly recorded below.

The complete acceptance sequence is startup; record; disconnect/finalize;
reopen; pause/seek/playback; camera/path interaction; render/export; close and
restore client state. Record the exact mod/shader configuration and tested
artifact hash for every claimed native result.

## GTNH startup on 2026-10-21

The tested single runtime JAR was `agenticreplay-1.7.10-0.8.0.jar`, SHA-256
`2bdffd2a0d7d952e6b38274651b715837c78a6165786edb6637e286746374b46`.
The isolated copy retained Galacticraft 3.4.34-GTNH, Angelica 2.2.28,
UniMixins 0.3.1, JarJar 0.3.7-beta and NEI 2.8.155-GTNH. No external ReplayMod
or separate AgenticReplay adapter was installed.

At 19:51:56 local time Forge reported `successfully loaded 325 mods`; its
resource-pack list included AgenticReplay and AgenticReplay Replay Engine.
Angelica subsequently reached hardware-paced frame rendering at 19:53:15.
This cleared the reproduced discovery, JarJar manifest access, NEI bytecode
pattern and Galacticraft overlay startup failures. It does not establish
in-world overlay or recording/export correctness.

The test client was stopped to relieve local memory pressure while preserving
the user's existing client. No recording round trip or export was completed;
the API was not observed responding during the earlier startup probe. Those
acceptance gates remain open. The disposable vanilla server fixture was not
used for a completed connection test.
