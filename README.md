# AgenticReplay

Forge 1.20.1 fork of [ReForgedPlay](https://github.com/ferriarnus/ReForgedPlay), based on [ReplayMod](https://github.com/ReplayMod/ReplayMod).
Agent control uses a versioned Java facade and an opt-in authenticated loopback HTTP endpoint. Existing UI remains usable.

## Enable the API

Add these JVM arguments to the Minecraft client:
```text
-Dagenticreplay.token=<random-token-of-at-least-32-characters>
-Dagenticreplay.port=8766
```

Without a token the HTTP server stays disabled. It binds only to IPv4 localhost. Use an SSH tunnel if the MCP gateway runs on another machine.
Do not install this mod on a dedicated server. Video rendering still needs a running graphical Minecraft client and FFmpeg.

POST to `http://127.0.0.1:8766/rpc` with `Authorization: Bearer <token>`:
```json
{"method":"capabilities","params":{}}
```
Successful responses contain `{"ok":true,"result":...}`. Failures contain `ok:false`, an error class and a message.
Times are milliseconds. File arguments are plain names within the configured replay/video directories, never arbitrary paths.
Calls run on the Minecraft client thread; render jobs run there as well. Requests are bounded to 64 KiB and wait up to 30 seconds.
A timeout has an uncertain outcome if execution already began: inspect state before retrying a mutation.

## Controls

| Area | Methods |
| --- | --- |
| State | capabilities, status |
| Live client | client.connect/disconnect/background, player.state/stop |
| Live actor | player.input/look/select/interact/fly/dismount, player.inventory.click, player.screen.close |
| Offline replay view | camera.options accepts viewDistance 2..32, overriding the recorded server clamp for cinematic framing |
| Actor sequences | player.sequence, player.sequence.status |
| Files | replay.list/open/close/rename/delete/process, process.status |
| Playback | playback.set (speed 0 pauses), playback.seek |
| Camera | camera.set/options/spectate, entities.list |
| Recording | recording.set, recording.marker, client.disconnect |
| Markers | markers.list/set |
| Paths | path.get/keyframe/remove/move/clear/interpolation/undo/redo/save/load/play/stop/repository/import/export/preview |
| Settings | settings.get/set |
| Export | render.start/status/pause/cancel, capture.start/status |

Example cinematic:
```json
{"method":"replay.open","params":{"name":"session.mcpr"}}
{"method":"path.clear"}
{"method":"path.keyframe","params":{"type":"time","time":0,"replayTime":1000}}
{"method":"path.keyframe","params":{"type":"time","time":10000,"replayTime":11000}}
{"method":"path.keyframe","params":{"type":"position","time":0,"x":0,"y":100,"z":0,"yaw":0,"pitch":20}}
{"method":"path.keyframe","params":{"type":"position","time":10000,"x":20,"y":110,"z":20,"yaw":90,"pitch":30}}
{"method":"render.start","params":{"name":"shot.mp4","width":1920,"height":1080,"fps":60}}
```
Use the returned `jobId` for render.status, render.pause (`paused` boolean), and render.cancel.
Only status and render job controls are permitted while a render is queued/running; terminal job status is retained until the next job.
Render settings include `renderMethod`, `preset`, `bitrate`, `antiAliasing`, `nameTags`, `alpha`, stabilization flags,
`fovX/fovY`, `sphericalMetadata`, `depthMap`, and `cameraPathExport`.
Custom executable/FFmpeg argument injection is deliberately not part of the remote API.

Recording must be enabled before connecting so login/chunk packets are captured. recording.set accepts start, pause, resume, stop.
Pause/stop use ReplayMod cut/split markers; packet capture continues until disconnect, which finalizes the recording.
Call `client.disconnect` to leave the live world and finalize capture without a menu. Saving is asynchronous; poll replay.list for the output.
API-enabled sessions finalize to the replay directory without a rename dialog.
Compatibility mismatches return an error; explicitly pass `allowModMismatch:true` to replay.open to proceed.

Position keyframes also accept `entityId` for spectator paths; wait for `entityTrackerReady` in status.
`path.import` accepts a `timeline` object using the ReplayStudio serialization returned by path.export, optionally a saved path `name`.
`path.repository` lists saved path names; `path.preview` applies the path at a supplied time without playback.
`camera.options` controls `suppressMovement`, `hideHud`, and `overlay`. Its `viewDistance` override applies only to the current replay, survives seeks, and restores the previous client distance when the replay closes through the GUI, `replay.close`, or `client.disconnect`. A new replay starts with its recorded server clamp.

For live player evidence, `client.hud` accepts `debug` and `hideHud` booleans.
`capture.live` returns the current game framebuffer as PNG base64 with its
dimensions and fresh player state. It retains the actual HUD/F3 display, requires
live gameplay with no screen or replay open, and is bounded to 1920x1080 pixels
and 6 MiB. It neither renders a replacement camera nor reads the desktop.

`capture.start` accepts width/height and optional `thumbnail:true`; poll capture.status with captureId for a PNG base64 preview.
Capture is queued and cannot overlap mutations. `replay.process` applies cut/split markers to a closed replay and returns processId;
poll process.status for progress and output file names. Originals are retained in ReplayMod's raw directory.
Marker names `_RM_START_CUT`, `_RM_END_CUT`, and `_RM_SPLIT` provide unattended trimming/splitting through markers.set and replay.process.

Runtime modpack and shader compatibility must be tested with the actual client. Third-party extensions and online publishing integrations are outside this client-control API.
GPL-3.0-or-later; upstream credits and the internal `replaymod` identifier are retained for compatibility. Forge shows one AgenticReplay mod entry.

## Live player control (0.5.0, API v3)

The existing replay methods remain available. Live controls act on the real connected player and use
Minecraft's native key processing, raycast, item/block/entity interaction, slot clicks, and flight ability packets.
They do not teleport, extend reach, grant creative mode, spawn items, or bypass server permissions.
Use replay `camera.*` and `path.*` for cinematic cameras; `player.*` operates only outside replay playback.

Join using the existing authenticated client session:
```json
{"method":"client.connect","params":{"address":"your-server:25565","recording":true}}
{"method":"player.state","params":{}}
```
Connecting is asynchronous. Poll `player.state` for `live:true` and `screen:"none"` before starting actions.
Recording defaults to enabled before the connection so login/chunk packets are retained. `recording:false`
explicitly disables multiplayer recording. Disconnect finalizes the recording as described above.

For unattended filming while using other applications, add `-Dagenticreplay.background=true` before
launching. This opts out of initial GLFW window focus and disables Minecraft's automatic pause menu on
focus loss for this client session. The native window stays hidden and its framebuffer remains available
for recording and export. Forge's separate early loading window must also be disabled with
  `earlyWindowControl=false` in the filming profile's `config/fml.toml`.
Keep each `-D` flag separated by whitespace. Native state reports GLFW's actual
`glfwFocused` and `windowVisible` attributes; Minecraft's `windowFocused` field
can initially be true before its first focus event even for a hidden window.
`client.background` with `{"enabled":true,"hidden":true}` can hide an already running game window and disable auto-pause
for an already running client, or `enabled:false` can restore it. It does not focus the window or send
desktop input. Close an existing menu with `player.screen.close`, then start a take. Screen closing is
allowed while paused; all other actions still reject a paused client. Poll the closing job and
`player.state` until the screen is `none` and `paused` is false before starting gameplay. The setting is
not explicitly saved by the API. Physical keys or mouse buttons in the Minecraft window still stop a take.

`player.state` reports exact double-precision coordinates, yaw/pitch, dimension, life/ground/flight/riding state,
selected hotbar slot (0..8), vehicle entity ID, crosshair target, current screen's simple class name,
container `syncId`, container slots, cursor stack, and the latest action job. No player state is invented when disconnected.

Actions are queued for client ticks. Each response includes `jobId`, `state`, `step`, `steps`, `stepTicks`,
and `error`. Poll `player.sequence.status` with that `jobId` until `succeeded`, `failed`, or `cancelled`.
Starting another action while one is active fails; use `player.stop` first. A successful job means the inputs
were executed; use a state condition to establish server outcomes such as boarding or arrival.

```json
{"method":"player.look","params":{"yaw":90,"pitch":15,"ticks":20}}
{"method":"player.input","params":{"keys":{"forward":true,"sprint":true},"ticks":40}}
{"method":"player.select","params":{"slot":2}}
{"method":"player.interact","params":{"action":"use"}}
{"method":"player.fly","params":{"flying":true}}
{"method":"player.dismount","params":{"ticks":4}}
{"method":"player.stop","params":{}}
```
Run those examples separately after each previous job finishes. Input keys are `forward`, `back`, `left`,
`right`, `jump`, `sneak`, `sprint`, `attack`, and `use`. A command holds only the supplied keys for its
finite duration, then releases them. Use `player.input` to hold attack for normal survival block breaking
or use for repeated placement. `player.interact` produces one native use/attack press at the crosshair.
Looking interpolates along the shortest yaw arc, with pitch restricted to -90..90. Flight requires the
server-provided `allowFlying` ability and an airborne player (except spectator mode); jump for a few
ticks first when standing on the ground. Jump/sneak then provide normal vertical flight controls.

Repeatable takes use one fully validated plan:
```json
{"method":"player.sequence","params":{"steps":[
  {"action":"look","yaw":90,"pitch":10,"ticks":10},
  {"action":"input","keys":{"forward":true},"ticks":20},
  {"action":"use"},
  {"action":"wait","until":{"riding":true},"ticks":100},
  {"action":"wait","until":{"position":{"x":80.5,"y":512,"z":80.5,"tolerance":2}},"ticks":1200},
  {"action":"dismount","ticks":4},
  {"action":"wait","until":{"riding":false},"ticks":100}
]}}
```
Coordinates here are illustrative; inspect the actual set and replace them before filming. Supported actions:
`input`, `look`, `select`, `use`, `attack`, `fly`, `dismount`, `wait`, `assert`, `click_slot`, `close_screen`.
The fields match the individual methods. `assert` requires an `until` condition and fails immediately
when it is false. `wait` without `until` waits for its tick duration; with `until` it advances when all
conditions match, and fails if its tick budget expires. Conditions support `riding`, `onGround`, `flying`,
`slot`, `screen` (`none` or the class name from state), `dimension`, and `position` with x/y/z and optional
distance `tolerance` (default 0.5, maximum 64 blocks).

Inventory interaction requires a freshly inspected container identity:
```json
{"method":"player.inventory.click","params":{"syncId":4,"slotId":0,"button":0,"clickAction":"PICKUP"}}
{"method":"player.screen.close","params":{}}
```
Supported click actions are `PICKUP`, `QUICK_MOVE`, `SWAP`, and `THROW`; button is 0/1, or hotbar 0..8
for `SWAP`. A stale `syncId`, a closed screen, or an out-of-range slot fails before the click.
These are native container operations; mod-specific non-container buttons are not exposed by this API.

Limits: 1..128 steps, 1..1200 ticks per step, and at most 12000 ticks per sequence. Native one-shot actions
take one tick; only input/look/wait/dismount have extended durations. A monotonic wall deadline also bounds
slow ticks. Gameplay movement, looking, selection, interaction, flight, and dismounting require no open
screen. Wait/assert/container operations may run with a screen open. Disconnect, death, player/world
replacement, pause, failure, completion, API stop, or physical keyboard/mouse-button input releases all
owned keys and cancels an active job when appropriate. Sneak/sprint leases work with toggle settings too.
Mouse movement alone does not cancel a take. Only the latest job is retained, so save its result before
starting a new one. There is no pathfinding or automatic terrain avoidance.

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
New recordings also include an actor-frame extension: it preserves authored movement/rotation timing
and makes teleports snap during playback/export, without vanilla remote-player interpolation delay.
The normal position packets remain for replay indexing. Exact timing requires playback with this
AgenticReplay version; older recordings without actor frames retain vanilla interpolation.
Quick Mode indexes these frames separately from ReplayStudio's packet cache, so forward/backward
seeks and exports using Quick Mode preserve the same timing. Cut/split prefixes keep only the latest
frame for each surviving actor and snap to that boundary pose. Unchanged idle motion is not recorded.
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
