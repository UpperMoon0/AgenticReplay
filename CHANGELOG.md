# Changelog

## 0.7.2

- Support optional elytra equipment and stepped standing/gliding poses in export motion tracks, with an output-frame flight animation clock. Restore pose, flight flags, animation clock, velocity and chest equipment before replay simulation and on export exit.

## 0.7.1

- Package the glTF implementations and Jackson 2.20 runtime required by camera motion receipts; check both distributables in CI.

- Add optional per-frame cinematic entity motion tracks to both export engines, independent of uneven replay packet timing. Restore overridden poses before simulation and at export exit.
- Preserve Forge native DH/Oculus filming controls through bounded optional public APIs.

## 0.7.0

- Preserve the 26.1.2 GUI extraction lifecycle while suppressing export overlays. This fixes black shader exports observed with Iris, Sodium and Complementary in the private filming client.
- Use the runtime commons-codec instead of the old embedded copy, avoiding Sodium's Hex.decodeHex linkage failure.
- Add current-frame capture and actual render-camera diagnostics to the 26.1.2 API.
- Add a standalone NeoForge 26.1.2 client port with the authenticated API, player controls, scripted actors, recording, replay, camera paths and export adapters.
- Consolidate the workspace into `common`, `forge-1.20.1` and `neoforge-26.1.2`, with targeted builds and a CI matrix.
- Share transport validation, action planners, actor timelines and replay camera-budget state across versions.
- Adapt actor profiles, skins, typed custom payloads, entity packets and framebuffer readback to 26.1.2.
- Preserve exact actor frames during normal playback, Quick Mode seeks and marker cut/split processing.
- Pin a protocol-775-compatible ReplayStudio distribution with checksum-verified reproduction; keep Minecraft's Guava, Gson, Netty and LWJGL libraries authoritative.
- Fix unattended recording publication, queued replay lighting/chunk compilation, screenshot frame timing and export framebuffer restoration.
- Keep the Forge 1.20.1 feature set and regression suite in its own module.

## 0.6.0

- Preserve actor movement/turning timing and instantaneous teleports in playback with exact actor frames alongside indexed vanilla packets.
- Restore actor frames during Quick Mode seeks and exports; squash cut/split frame history to one boundary pose per surviving actor and omit unchanged idle motion.
- Preserve the immediately preceding actor pose when catch-up frames share a timestamp, including teleport-then-move sequences in Quick Mode.
- Combine native capture, hidden-window diagnostics and replay-scoped view distance from PR #3.
- Add 32 separately scripted film actors with independent identities, real-player skins, scenes and cancellation.
- Preserve actors and signed/default skins in replay packets; include a two-character Python filming/export example.
- Target Forge 1.20.1 throughout the implementation, packaging and CI.

## 0.5.0

- Preserve hidden, unfocused startup through Forge's native window handoff and expose actual GLFW focus/visibility diagnostics.

- Allow screen recovery while paused, retaining pause checks for every other action and sequence transition.
- Add API v2 live actor state, bounded native movement/looking/use/attack, hotbar selection, permitted flight, and dismounting.
- Add repeatable client-tick sequences with conditional waits, assertions, progress, cancellation, input cleanup, and tick/wall-time limits.
- Add native server connection with recording enabled before login, guarded container slot clicks, and screen closing.
- Release controlled inputs on completion, failure, stop, pause, world/player changes, death, disconnect, and physical keyboard/mouse-button takeover.
- Preserve existing replay/recording/camera/export methods and document live control separately from replay cameras.
- Add opt-in background startup without initial window focus, session-only auto-pause control, and native held mining without capturing the desktop cursor.
- Reject enabling flight while grounded and require an actual handled container screen for slot clicks.
- Add regression coverage for duration, cleanup, context loss, job isolation, condition timeouts, plan validation, and million-height coordinates.
