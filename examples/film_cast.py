"""Record two independent characters and optionally export a camera shot. Python 3, no packages."""
import argparse
import json
import os
import time
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skin-a", required=True, help="Real Minecraft username for character A")
    parser.add_argument("--skin-b", required=True, help="Real Minecraft username for character B")
    parser.add_argument("--x", type=float, required=True)
    parser.add_argument("--y", type=float, required=True)
    parser.add_argument("--z", type=float, required=True)
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--video", help="Optional .mp4 name: disconnect, open recording, and export")
    args = parser.parse_args()
    token = os.environ["AGENTICREPLAY_TOKEN"]

    def rpc(method, **params):
        request = urllib.request.Request(
            f"http://127.0.0.1:{args.port}/rpc",
            data=json.dumps({"method": method, "params": params}).encode(),
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=35) as response:
            body = json.load(response)
        if not body["ok"]:
            raise RuntimeError(f"{method}: {body.get('message', body)}")
        return body["result"]

    def wait(check, timeout=90):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            result = check()
            if result:
                return result
            time.sleep(0.25)
        raise TimeoutError("Timed out waiting for the filming client")

    status = rpc("status")
    if status["replayOpen"] or not status["player"]["live"]:
        raise RuntimeError("Join a live world with recording enabled before running this example")
    if not status["recordingAvailable"]:
        raise RuntimeError("Enable recording before joining the world")
    existing = {replay["name"] for replay in rpc("replay.list")}
    rpc("recording.set", state="start")
    created = []
    try:
        for actor_id, name, skin, offset in (
            ("cast_a", "CharacterA", args.skin_a, -2),
            ("cast_b", "CharacterB", args.skin_b, 2),
        ):
            rpc("actor.spawn", actorId=actor_id, name=name, skin={"username": skin},
                x=args.x + offset, y=args.y, z=args.z)
            created.append(actor_id)

        def ready():
            states = [rpc("actor.state", actorId=actor_id) for actor_id in created]
            for actor in states:
                if actor["state"] == "failed":
                    raise RuntimeError(actor["error"])
            return all(actor["state"] == "ready" for actor in states)

        wait(ready)
        scripts = {
            "cast_a": [
                {"action": "equip", "slot": "mainhand", "item": "minecraft:diamond_sword"},
                {"action": "look", "yaw": -90, "pitch": 0, "ticks": 10},
                {"action": "move", "x": args.x - 0.5, "y": args.y, "z": args.z, "ticks": 40},
                {"action": "swing"},
                {"action": "wait", "ticks": 49},
            ],
            "cast_b": [
                {"action": "equip", "slot": "mainhand", "item": "minecraft:shield"},
                {"action": "look", "yaw": 90, "pitch": 0, "ticks": 10},
                {"action": "move", "x": args.x + 0.5, "y": args.y, "z": args.z, "ticks": 40},
                {"action": "pose", "pose": "crouching"},
                {"action": "wait", "ticks": 30},
                {"action": "pose", "pose": "standing"},
                {"action": "wait", "ticks": 18},
            ],
        }
        # One RPC validates the entire cast and queues both independent clocks for the next tick.
        rpc("recording.marker", name="cast_scene_start")
        rpc("actor.scene", scripts=scripts)

        def completed():
            states = [rpc("actor.state", actorId=actor_id)["script"] for actor_id in created]
            for job in states:
                if job["state"] in ("failed", "cancelled"):
                    raise RuntimeError(job["error"])
            return all(job["state"] == "succeeded" for job in states)

        wait(completed)
        rpc("recording.marker", name="cast_scene_end")
        print("Both character scripts completed.")
    finally:
        for actor_id in created:
            rpc("actor.despawn", actorId=actor_id)

    if not args.video:
        print("Disconnect when you finish filming to save the replay.")
        return
    rpc("client.disconnect")

    def saved():
        names = [item["name"] for item in rpc("replay.list") if item["name"] not in existing]
        if len(names) > 1:
            raise RuntimeError("Multiple new recordings; choose one explicitly with replay.open")
        return names[0] if names else None

    replay = wait(saved)
    rpc("replay.open", name=replay)
    markers = rpc("markers.list")
    starts = [marker["time"] for marker in markers if marker.get("name") == "cast_scene_start"]
    ends = [marker["time"] for marker in markers if marker.get("name") == "cast_scene_end"]
    if not starts or not ends:
        raise RuntimeError("Scene markers missing in saved replay")
    start, end = max(starts), max(ends)
    duration = end - start
    if duration <= 0:
        raise RuntimeError("Scene has no recorded duration")
    rpc("path.clear")
    for time_ms, replay_time in ((0, start), (duration, end)):
        rpc("path.keyframe", type="time", time=time_ms, replayTime=replay_time)
        rpc("path.keyframe", type="position", time=time_ms, x=args.x, y=args.y + 2,
            z=args.z - 7, yaw=0, pitch=10)
    job = rpc("render.start", name=args.video, width=1280, height=720, fps=30)

    def exported():
        state = rpc("render.status", jobId=job["jobId"])
        if state["state"] in ("failed", "cancelled"):
            raise RuntimeError(state["error"] or state["state"])
        return state if state["state"] == "succeeded" else None

    wait(exported, timeout=1800)
    print(f"Exported {args.video} from {replay}.")


if __name__ == "__main__":
    main()
