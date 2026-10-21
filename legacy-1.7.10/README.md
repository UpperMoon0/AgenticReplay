# AgenticReplay for Forge 1.7.10 and UniMixins

Install **one** `agenticreplay-1.7.10-0.8.0.jar`, with Forge 10.13.4.1614 and
UniMixins 0.3.1 or later. Remove the old `agenticreplay-1.7.10-0.7.x.jar` adapter
and the separately installed `replaymod-1.7.10-2.5.2.jar` first. Keep GTNH's
Galacticraft and Angelica. Do not add OptiFine beside Angelica.

The artifact contains the maintained ReplayMod 2.5.2 engine fork, its original
recording/replay/camera/path/render UI, and the Java 8 agent adapter. The internal
`replaymod` ID/version and module IDs are retained for engine compatibility.
The agent reports version 0.8.0 and an `engineBuild` object in capabilities/status.
Do not install this client artifact on a dedicated server.

## Build and ownership

```sh
python3 legacy-1.7.10/scripts/prepare_replaymod.py
./gradlew -p legacy-1.7.10 build
python3 scripts/check_engine_jar.py legacy-1.7.10 legacy-1.7.10/build/libs/agenticreplay-1.7.10-0.8.0.jar
```

Gradle 8.14 runs on Java 21; RetroFuturaGradle 1.4.9 compiles the adapter and
engine compatibility replacements using Java 8/MCP stable-12. Runtime output
is in `build/libs`; internal adapter/development inputs are in
`build/engine-inputs` and must not be installed separately. The sources JAR
contains the replacement/adapter/shared source, corresponding upstream source
and build inputs. The repository retains the complete original source snapshot.

This is a **binary-backed source-overlay fork**, not a claim that every
historical engine class is recompiled. The unchanged official base is SHA-256
`e8b0379ed3b03b3028145c71962aa141757844f2eb7b82ce5390a9cee5a8ce96`;
the preparation script verifies it and its normalized compile archive. Source
replacements are compiled/reobfuscated here and replace their whole original
class families during deterministic assembly. No user edits or external engine
download is needed. See [the design and remaining source-build gate](../docs/ENGINE_FORK_DESIGN.md).

The launch profile deliberately uses UniMixins bootstrap. It removes the old
ReplayMod cascaded tweaker and forces normal FML mod discovery. This change is
confined to the legacy artifact. The GL transformer keeps actual GL calls in
place, invokes them once, notifies state tracking afterward and recomputes stack
sizes, preserving Galacticraft's required injection target.

## Agent controls and limits

Use JVM properties `-Dagenticreplay.token=<at-least-32-random-characters>` and
`-Dagenticreplay.port=8766`. The API is opt-in and authenticated on IPv4 loopback.
Query `capabilities` before sending commands. Existing recording controls,
finalization, replay files/open/close, seek/speed, camera, marker, connection and
basic live-player controls remain available.

Remote path editing/export, scripted actors, native capture, held movement and
player sequences remain unavailable. Use the retained ReplayMod UI for path
editing/rendering. Recording must be armed before connection; pause/stop use
cut/split markers, and disconnect finalizes asynchronously. Poll `replay.list`
for the saved file. Replays remain tied to the 1.7.10 packet protocol.

Build/bytecode tests do not establish graphical compatibility. Shader export,
terrain completion and free-camera hand rendering require their own validation;
the [pinned Angelica findings and test procedure](../docs/SHADER_WORKFLOW.md)
remain applicable. Runtime results are recorded separately in the engine audit.

## Sources and notices

ReplayMod source base: `ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885` (2.5.2), with
jGui `6641836321f4583e6f56d8ceb59da452213396e7` and translations
`63b7a8bea1ab9a9f005c9ae0e1c54b4432cbc9e6`. Original build/source/notices live
in `engine-inputs`. Legacy-specific source is GPL-3.0-or-later; the shared
Java 8 contracts retain their LGPL notice. Bundled third-party notices are
preserved. New compatibility code does not alter other installed mod JARs.

The user's MC-Modding-Src 1.7.10 vanilla implementation and the exact Forge,
Galacticraft 3.4.34-GTNH, Angelica 2.2.28 and UniMixins 0.3.1 sources were
inspected. No vanilla/private repository source is redistributed.
