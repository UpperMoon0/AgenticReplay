# AgenticReplay

Scriptable ReplayMod/ReForgedPlay fork for filming Minecraft videos. This branch targets **NeoForge 1.20.4**;
the separate `1.20.1` branch targets Forge 1.20.1. Install on a graphical Minecraft client, not a dedicated server.
Based on [ReForgedPlay](https://github.com/ferriarnus/ReForgedPlay) and [ReplayMod](https://github.com/ReplayMod/ReplayMod).

## Enable scripting

Add these JVM arguments in your launcher:

```text
-Dagenticreplay.token=<random-token-of-at-least-32-characters>
-Dagenticreplay.port=8766
-Dagenticreplay.background=true
```

Without a token, the API is disabled. It binds to `127.0.0.1` only. Calls require
`Authorization: Bearer <token>` and `Content-Type: application/json`:

```json
{"method":"capabilities","params":{}}
```

POST requests to `http://127.0.0.1:8766/rpc`. Responses contain `{"ok":true,"result":...}`;
errors contain `{"ok":false,"error":...,"message":...}`. Requests are limited to 64 KiB.
API version 3 includes recording, playback, camera, timeline, rendering and live-player controls.
Use `capabilities` to discover the complete method list. Video export requires FFmpeg.

## Multiple scripted characters

One filming client can create up to **32 independent characters** with distinct actor IDs, entity IDs,
names and UUIDs. Each has its own script, progress, job ID and cancellation state. Their scripts never
use the logged-in player's keyboard or mouse. Two characters can share a real player's skin
while retaining separate identities.

Characters are cinematic entities in this client: their movement is authored directly, so paths can walk,
jump, fly, or cross terrain. They do not mine/place blocks or act as authenticated server accounts.
Film in loaded chunks. Other clients on the server do not see this cast; the filming client and its
saved replay do. Use normal `player.*` controls for actual gameplay by the logged-in player.

Enable ReplayMod recording **before joining** the world. You can use:

```json
{"method":"client.connect","params":{"address":"localhost:25565","recording":true}}
```

In a live, unpaused world, create characters with different real skins:

```json
{"method":"actor.spawn","params":{"actorId":"alice","name":"CharacterA","skin":{"username":"Notch"},"x":0,"y":64,"z":0,"yaw":-90}}
{"method":"actor.spawn","params":{"actorId":"bob","name":"CharacterB","skin":{"username":"jeb_"},"x":4,"y":64,"z":0,"yaw":90}}
```

Coordinates are feet positions. `actorId` allows 1..48 letters, digits, underscores or hyphens;
`name` allows 1..16 letters, digits or underscores. The default name is the actor ID.
Skin options are mutually exclusive:

- Omit `skin` to copy the logged-in player's skin.
  Built-in default avatars also keep the source player's appearance despite the actor's separate UUID.
- `{"username":"MinecraftName"}` resolves a real player's current skin.
- `{"uuid":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"}` resolves a real player by UUID.
- `{"value":"<base64 textures property>","signature":"<Mojang signature>"}` uses an existing signed skin property.
  The signature is verified; texture URLs must point to `textures.minecraft.net`. Classic/slim models and
  capes use the profile's metadata. This supports repeatable character designs.

Profiles and textures load asynchronously. Poll `actor.state` until `state` is `ready` before starting
scripts. `loading` means lookup/download is pending; `failed` includes an error.
Lookup and texture stages each have a 30-second timeout. Explicit lookup failures fail visibly.

```json
{"method":"actor.state","params":{"actorId":"alice"}}
{"method":"actor.list","params":{}}
```

Run a character separately:

```json
{"method":"actor.sequence","params":{"actorId":"alice","steps":[{"action":"move","x":2,"y":64,"z":0,"ticks":40},{"action":"swing"},{"action":"wait","ticks":20}]}}
{"method":"actor.sequence","params":{"actorId":"bob","steps":[{"action":"pose","pose":"crouching"},{"action":"wait","ticks":60},{"action":"pose","pose":"standing"}]}}
```

Or queue all characters in **one synchronized scene**. Every script and referenced item/NBT is validated
before any character starts; unknown, loading or busy characters reject the whole request:

```json
{"method":"actor.scene","params":{"scripts":{"alice":[{"action":"move","x":2,"y":64,"z":0,"ticks":40},{"action":"swing"}],"bob":[{"action":"look","yaw":90,"pitch":0,"ticks":20},{"action":"pose","pose":"crouching"},{"action":"wait","ticks":20}]}}}
```

Both queues start on the next actor tick. At normal client speed, 20 ticks = 1 second. Pausing Minecraft
freezes the scripts. `result.script` in `actor.state` reports `queued`, `running`, `succeeded`,
`cancelled` or `failed`. Completed characters retain their final state.
A script supports 1..128 steps, 1..1200 ticks per step and at most 12000 ticks in total.
`ticks` defaults to 1; only `move`, `look`, and `wait` accept a duration longer than one tick.

| Action | Parameters | Behavior |
| --- | --- | --- |
| `move` | `x`, `y`, `z`, `ticks`, optional `yaw`, `pitch` | Linear path with walking animation; optionally turns while moving |
| `teleport` | `x`, `y`, `z` | Immediate reposition |
| `look` | `yaw`, `pitch`, `ticks` | Turns over the shortest yaw arc; pitch -90..90 |
| `pose` | `pose`, optional `sprinting` | `standing`, `crouching`, `swimming`, `fall_flying`, `sleeping` |
| `equip` | `slot`, `item`, optional `count`, `nbt` | `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`; installed item ID and optional SNBT |
| `swing` | optional `hand` | `mainhand` (default) or `offhand` swing |
| `hurt` | none | Visual hurt animation |
| `wait` | `ticks` | Holds the character's state |

Stop one character without stopping the others, or manage the cast:

```json
{"method":"actor.stop","params":{"actorId":"alice"}}
{"method":"actor.stopAll","params":{}}
{"method":"actor.despawn","params":{"actorId":"alice"}}
{"method":"actor.clear","params":{}}
```

Stop retains a character's appearance and position. Despawn removes it and frees the actor ID.
Disconnects, dimension changes, and world replacements clear the old cast and cancel pending lookups.
Physical keyboard/mouse takeover stops `player.*` automation; it does not cancel film characters.

## Recording and video export

Actors' profile/skin properties, spawn/despawn, positions, rotations, poses, equipment and animations are
written into the normal `.mcpr` packet stream. They remain visible when the replay is reopened and rendered;
actor scripts do not run during playback. Skins use Minecraft's normal texture cache/service.
Characters spawned before a recorder becomes available are registered when packet capture begins.

`recording.set` accepts `start`, `pause`, `resume`, `stop`. Pause/stop use ReplayMod cut/split markers;
packet capture continues until `client.disconnect`, which finalizes the recording asynchronously.
Poll `replay.list` for the saved file. API sessions finalize without a rename dialog.

The [two-character filming example](examples/film_cast.py) uses only Python's standard library.
Set `AGENTICREPLAY_TOKEN` to match the launch token, join a recording-enabled world, then run:

```text
python examples/film_cast.py --skin-a Notch --skin-b jeb_ --x 0 --y 64 --z 0
```

Add `--video cast-demo.mp4` to disconnect, reopen the new recording, construct a camera path between
scene markers, and export a 1280x720/30fps shot. Output names must be new; existing files are preserved.
Use actual loaded filming coordinates for your world. The example owns only `cast_a` and `cast_b`.

Manage recordings with `replay.list/open/close`, `playback.seek/set`, `camera.set/spectate`, `entities.list`,
`path.keyframe/play/preview/save/load`, and `render.start/status/pause/cancel`.
Camera times are milliseconds. Player and actor script durations are ticks.
Call `status` for recording, cast, player, capture, render and path state.

## Development

Java 17. Run `./gradlew build` (Windows: `gradlew.bat build`). CI builds the NeoForge 1.20.4 client and
runs regression tests for independent character clocks, atomic scene validation, skin validation,
packet round trips, and ported player/transport/path behavior.
Native rendering and modpack compatibility also require validation in the actual graphical client.

GPL-3.0-or-later; upstream credits and ReplayMod's internal ID remain intact.
