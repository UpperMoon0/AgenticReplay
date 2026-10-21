# Runtime validation for the engine fork change

Builds and regressions are recorded separately from native client results.
This file must not imply that source adoption establishes rendering parity.

| Lane | Native validation for 0.8.0 |
| --- | --- |
| Forge 1.7.10 / GTNH 2.9.0-RC-2 | Startup passed in the isolated profile below: Forge loaded 325 mods, including both fork identities, and Angelica reached frame pacing. A separate minimal Angelica fixture passed recording/replay/export; final full-stack repeat remains outstanding |
| Forge 1.20.1 | Passed loopback recording/finalize/reopen/seek, authored actors, camera capture and ten-frame export with Embeddium 0.3.30; client usable afterward |
| NeoForge 1.21.1 | Native startup, recording/replay/path/export UI acceptance is outstanding; compile/package success does not certify it |
| NeoForge 26.1.2 | Passed loopback recording/finalize/reopen/seek, authored actors, camera capture and ten-frame export; client usable afterward |

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

## Legacy Angelica round trip on 2026-10-21

The isolated minimal client uses UniMixins 0.3.1, Angelica 2.2.28, GTNHLib
0.11.52 and lwjgl3ify 3.0.37 (21 logical mods including the separate test probe),
with shader packs disabled. Tested runtime SHA-256:
`0eba50e1480286de46a6aeceb2478a1665631057fbf45aee495c8719b1876df9`.
A disposable vanilla 1.7.10 loopback server supplied the recording fixture.
Recording, disconnect/finalize, reopen, pause/seek/playback, camera path, export
and close passed. All ten 320x180 frames had terrain coverage near 67.5%,
compared with the paused baseline's 67.1%; the renderer retained 21 visible
sections in every frame. The optional native probe is a separate test artifact,
excluded from the production JAR and release upload.

This test reproduced and fixed two engine defects: synthetic player packets
could overtake a queued join packet, and the old vanilla terrain wait did not
wait for Angelica. The FIFO recorder and version-gated graph/build/upload
barrier are compiled source. Graph searches advance the actual owning frame
counter so repeated searches do not collapse visibility to a single section.
This does not certify shader animation clocks, hand rendering or modded worlds.

## Modern native round trips on 2026-10-21

Both modern API clients ran under Xvfb/Mesa llvmpipe on cloud-1, in disposable
workspaces and loopback fixtures. No on-prem-1 execution was used.

- Forge 1.20.1 runtime SHA-256:
  `0a62efc7852856a94957855f8fd3b40f53b6c440286d77db3b99c6bca477b7c9`.
  Embeddium 0.3.30 initially produced an empty first frame, then progressively
  filled terrain. The fixed barrier repeats WorldRenderer's normal setup entry
  until graph/build/upload queues settle, preserving Embeddium's frame counter
  and render-device scope. All ten exported frames now contain 33,433–33,519
  terrain pixels of 57,600. Both authored actors survive recording and seek.
  Export cleanup disables Flawless Frames. Shader packs were disabled.
- NeoForge 26.1.2 runtime SHA-256:
  `f42530dc013aaab7a71ad8c97ba0890443bfafbfceabeccc7e47248fac423da2`,
  from commit `4bde4d409360152575121ae9b6f8119c496b798e`.
  Java 25.0.3, isolated NeoForge server fixture. Both authored actors, seek,
  replay capture, ten-frame encoded export and post-export close passed.

The UI-only NeoForge 1.21.1 native acceptance and final full-GTNH repeat remain
open. Full historical legacy source compilation is a separate acceptance gate.
