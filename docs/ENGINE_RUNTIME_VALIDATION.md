# Runtime validation for the engine fork change

Builds and regressions are recorded separately from native client results.
This file must not imply that source adoption establishes rendering parity.

| Lane | Native validation for 0.8.0 |
| --- | --- |
| Forge 1.7.10 / GTNH 2.9.0-RC-2 | Startup passed in the isolated profile below: Forge loaded 325 mods, including both fork identities, and Angelica reached frame pacing. Full GTNH integrated-world startup/recording/finalize/reopen/seek/playback/path/export/close passed; separate minimal Angelica fixture also passed |
| Forge 1.20.1 | Passed loopback recording/finalize/reopen/seek, authored actors, camera capture and ten-frame export with Embeddium 0.3.30; client usable afterward |
| NeoForge 1.21.1 | Passed native recording/finalize/reopen/seek/playback/camera-path/export/close through a separate opt-in fixture; product remains UI-only |
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

Full historical legacy source compilation remains a separate acceptance gate.

## Full GTNH integrated-world acceptance on 2026-10-21

The updated fork SHA-256 is
`305215f641b9b45197dc95dde4a8820f12de995977a0e9d16fd1a17ad47bc7b1`.
The isolated GTNH 2.9.0-RC-2 copy retains Galacticraft 3.4.34-GTNH, Angelica
2.2.28, UniMixins 0.3.1, NEI 2.8.155-GTNH, JarJar and the remaining pack mods.
The separate native fixture adds one test mod (243 top-level JARs).

A fresh creative flat integrated world passed recording/finalize/reopen,
pause/seek/playback, camera-path construction, ten-frame 320x180 MP4 export
and close/client restoration. Replay: `2026_10_21_22_22_32.mcpr`.
The paused baseline contains 57.3% terrain; all ten exported frames contain
38,383–38,434 terrain pixels of 57,600 and over 3,100 distinct colors.
Shader packs were disabled. This exercises the complete pack's registries on
both sides of the integrated connection; it is not a remote GTNH-server test.

The preceding vanilla-server fixture was rejected by the full pack with a
packet-size mismatch. It is retained as a failed fixture, not counted as a
replay-engine success or silently bypassed. Slow integrated-world initialization
also exceeded one state-poll deadline; the test resumed the same test-owned
world after initialization. Read-only waits may retry timed-out polls; mutations
are never automatically retried. The completed client and fixture processes
were stopped after their exact test identities were checked.

## NeoForge 1.21.1 native acceptance on 2026-10-21

Runtime `agenticreplay-1.21.1-0.8.0.jar`, SHA-256
`847db9491e59f4c14a29f22e73b38a8e15b9a1be4bc951b0ba8f08c4bc405391`.
Java 21.0.11, Xvfb/Mesa llvmpipe, Embeddium 1.0.8-beta.367+mc1.21,
shader packs disabled and an isolated vanilla 1.21.1 loopback server.
Recording/finalize/reopen, pause/seek/playback, camera path, ten-frame MP4
export and close passed. All frames contain 73.2–74.6% terrain. Three terrain
barrier regressions pass and the packaged engine/library audit passes.

Native startup exposed duplicate MixinExtras modules: Embeddium's transitive
0.3.5 is now excluded because NeoForge supplies 0.4.1. The restrictions payload
is optional, matching the client-only recorder contract; NeoForge 21.1.82's
PayloadRegistrar/NetworkRegistry source confirms why a required channel refused
a vanilla server. Recorder finalization now runs after packet writes queued on
the client executor, including aborted login/configuration connections.

The original one-pass Embeddium export produced two sky-only frames followed
by progressively appearing terrain. The new barrier follows the normal owning
WorldRenderer setup entry until graph/build/upload queues settle. Inspected
Embeddium source: `24f4d401b6ed3dbe10038267cd07024e3d6d3356`; its version
algorithm gives 1.0.8-beta.367 from mod_version 1.0.7 and 367 commits since its
recorded base. Actual installed JAR descriptors/fields were checked as well.

The opt-in nativeTest source set and authenticated loopback fixture are excluded
from production JARs; CI compiles the fixture and distribution validation rejects
any leaked nativecheck classes. No agent API parity is added to this UI lane.
The fixture waits for camera readiness and prevents reentrant command dispatch
during nested loading renders. Its initial 2 GiB run failed during that early
seek; the corrected fixture uses 4 GiB and passed without packet-handler errors.
