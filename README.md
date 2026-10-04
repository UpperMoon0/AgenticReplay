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
| Files | replay.list/open/close/rename/delete |
| Playback | playback.set (speed 0 pauses), playback.seek |
| Camera | camera.set, camera.spectate, entities.list |
| Recording | recording.set, recording.marker |
| Markers | markers.list/set |
| Paths | path.get/keyframe/remove/move/clear/interpolation/undo/redo/save/load/play/stop |
| Settings | settings.get/set |
| Export | render.start/status/pause/cancel |

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
API-enabled sessions finalize to the replay directory without a rename dialog.
Compatibility mismatches return an error; explicitly pass `allowModMismatch:true` to replay.open to proceed.

This is the initial control API, not a claim that every extra/plugin feature is exposed. Runtime modpack and shader compatibility must be tested with the actual client.
GPL-3.0-or-later; upstream credits and mod identifiers are retained for compatibility.
