# AgenticReplay for Forge 1.7.10

This Java 8 client adapter provides AgenticReplay's authenticated loopback API over the **official ReplayMod 1.7.10-2.5.2 engine**. Install both jars. It does not replace the existing Forge 1.20.1 engine or its features. The historical engine already supplies actual packet recording, `.mcpr` replay playback, free camera, path editing and video rendering through its original UI.

This target is a capability-limited port, not API parity with 1.20.1. Query `capabilities` before sending commands. There is no remote render/export, remote path editing, scripted actor injection, native capture, held movement or player-sequence API yet. The original ReplayMod UI remains available for path editing and FFmpeg export. Existing modern replay files cannot be converted into the 1.7.10 packet protocol by this adapter.

Shared HTTP/security, filename safety and JSON validation live in [common](../common/README.md), compiled from the same files as the modern version. Minecraft/ReplayMod engine adapters remain version-specific. For the shader workflow, see [record without shaders, export with shaders](../docs/SHADER_WORKFLOW.md).

## Build

From the repository root:

```sh
python3 legacy-1.7.10/scripts/prepare_replaymod.py
./gradlew -p legacy-1.7.10 build
```

Gradle 8.8 runs on Java 17 or 21; the mod compiles and tests with a provisioned Java 8 toolchain. RetroFuturaGradle 1.4.9 uses Forge 10.13.4.1614 and MCP `stable_12`. The engine was built for Forge 10.13.4.1558; runtime compatibility with a particular modpack still needs testing. The separate project deliberately does not evaluate the modern Loom build. Both game artifacts take version 0.7.0 from the root version property; the legacy FML annotation is generated from that same value.

The preparation script downloads the official engine from replaymod.com and verifies SHA-256 `e8b0379ed3b03b3028145c71962aa141757844f2eb7b82ce5390a9cee5a8ce96`. The official archive contains a duplicate `assets/` directory record. The preparation script creates a separate compile-only ZIP without directory records; Gradle verifies that every class/resource byte exactly matches the hash-pinned original before deobfuscation. The official runtime jar remains untouched. It is never shaded into the adapter. Output is `legacy-1.7.10/build/libs/agenticreplay-1.7.10-0.7.0.jar`; use the reobfuscated main jar, not the sources/dev jars. CI uploads build output and test reports without publishing a release.

## Run

Use a Java 8 graphical Forge 1.7.10 client, with the adapter and official `replaymod-1.7.10-2.5.2.jar` in `mods`. Do not install either on a dedicated server. Add:

```text
-Dagenticreplay.token=<random-token-of-at-least-32-characters>
-Dagenticreplay.port=8766
```

With no token the API stays disabled. It binds only to `127.0.0.1`. POST JSON to `/rpc` with `Authorization: Bearer <token>`. The request/response envelope matches the modern API: `{"method":"status","params":{}}` returns `{"ok":true,"result":...}`. Inputs are capped at 64 KiB. Client work uses a bounded render-tick queue, including while replay time is paused. It never holds a queue lock while calling the engine, avoiding legacy sender/queue lock inversion during seeks. Additional seeks and camera pose/spectator changes are rejected until `status.seekInProgress` becomes false. A 30-second timeout cancels work that has not started; already-started work has an uncertain result, so inspect state before retrying.

Supported methods:

- `capabilities`, `status`
- `client.connect` (`address`, optional `recording`, default true), `client.disconnect`, `client.background` (`enabled`, or explicit `pauseOnLostFocus`; window hiding unsupported)
- `player.state`, `player.look` (immediate `yaw`, `pitch`), `player.select` (`slot` 0..8), `player.screen.close`
- `replay.list/open/close/rename/delete`
- `recording.set/marker`
- `playback.set/seek`
- `camera.set/options/spectate`, `entities.list`
- `markers.list/set`

Start recording before connection; missing login/chunk packets cannot be recovered afterward. Pause/resume/stop operate the actual legacy recording controls and cut/split markers. Disconnect finalizes capture asynchronously; poll `replay.list` until the file appears. The rename dialog is disabled for API-armed recordings. Server-required client mods must be installed; Forge handshake/network processing is owned by the historical engine, not replaced by synthetic packet stubs.

Replay filenames are constrained to the replay directory; links/traversal are rejected. `replay.delete` moves a closed file into `agenticreplay-trash` for recovery. Replay opening checks mod compatibility unless explicitly allowed. Spectating is restricted to living entities because that is the 1.7.10 engine contract. Rendering and modded replay compatibility need an in-game smoke test; unit/build tests alone cannot establish them.

## GTNH and Angelica

Keep the modpack’s Angelica installation; do not add OptiFine alongside it. `capabilities.renderCompatibility` and `status.renderCompatibility` report the loaded Angelica version and explicitly leave shader export unverified. Source inspection of GTNH’s listed Angelica 2.2.28 found unsynchronized shader animation time, an ineffective ReplayMod terrain-completion barrier, and a separate hand-render event. These are potential footage defects, not evidence that a tested recording/replay round trip failed. Other versions require their own validation. See [the shader workflow and pinned evidence](../docs/SHADER_WORKFLOW.md) before using this stack for footage. No speculative renderer patches are installed.

## Provenance and licensing

The private user-provided `UpperMoon0/MC-Modding-Src` 1.7.10 tree was inspected for exact vanilla contracts. Its audit identifies community MCP stable-12 names, not Mojang mappings. No decompiled vanilla source, Mojang jar, mappings archive, or private repository content is redistributed here.

The runtime is official [ReplayMod 2.5.2 source](https://github.com/ReplayMod/ReplayMod/tree/ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885), downloaded through the [official downloads page](https://www.replaymod.com/download/). Upstream identifies this as GPL-3.0-or-later. This new adapter module is offered under GPL-3.0-or-later; its license copy is in `LICENSE.md`. The shared common code retains LGPL-3.0 and its separate license notice; the modern target retains its existing LGPL license. Distribution must retain applicable notices and provide corresponding adapter source; the engine remains a separate download under its own terms. The 2.5.2 runtime is historical; this project does not claim ongoing upstream 1.7.10 maintenance.

The bridge uses typed engine APIs except narrowly pinned private UI fields for recording buttons and marker refresh. Those fields are checked at initialization, so incompatible engine changes fail visibly rather than silently losing recording controls.

## Verification record

- Local Java 8 compilation, reobfuscation and full legacy build passed.
- Twenty-nine JUnit tests passed in the legacy build (including the sixteen shared-contract tests). Transport, render-queue, replay-path and pinned engine ABI tests cover authentication, bounds, timeout cancellation, queue isolation, filename safety four private engine fields, and truthful renderer compatibility reporting.
- Independent binary linkage review resolved referenced Minecraft/Forge/ReplayMod members against the exact 1.7.10 runtime inputs; adapter bytecode targets Java 8.
- No graphical Minecraft session has been launched in this environment. No recording/replay round trip, modded-server handshake or rendered export is claimed verified yet.

Required in-game acceptance: install both jars, query capabilities/status, connect with recording enabled, add a marker, disconnect and wait for `.mcpr`, open it, pause/seek/resume, move/spectate the camera, close via both API and UI, verify HUD/FOV restoration, then exercise the original path/export UI. Repeat against the intended modpack before relying on production captures.
