"""Exercise the port against the isolated local smoke server and client run configs."""
import base64
import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

URL = "http://127.0.0.1:18766/rpc"
TOKEN = "local-port-smoke-token-26-1-2-development"
OUT = Path(__file__).resolve().parents[1] / "neoforge-26.1.2/build/native-smoke"
OUT.mkdir(parents=True, exist_ok=True)

def rpc(method, params=None):
    request = urllib.request.Request(URL, data=json.dumps({"method": method, "params": params or {}}).encode(),
        headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"})
    try:
        response = urllib.request.urlopen(request, timeout=35)
    except urllib.error.HTTPError as error:
        response = error
    result = json.load(response)
    if not result.get("ok"):
        raise RuntimeError(method + ": " + str(result))
    return result["result"]

def wait(method, predicate, params=None, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = rpc(method, params)
        if predicate(value):
            return value
        time.sleep(.25)
    raise TimeoutError(method + ": " + str(value))

def verify_scene(path):
    # This disposable flat-world fixture contains grass and two brown/purple avatars.
    # Require their pixels: encoder success must not accept empty/sky-only frames.
    rgb = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(path), "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"])
    pixels = list(zip(rgb[0::3], rgb[1::3], rgb[2::3]))
    grass = sum(g > r*1.1 and g > b*1.3 for r, g, b in pixels)
    actors = sum(r > g*1.15 and r > b*1.05 and r > 40 for r, g, b in pixels)
    assert grass > len(pixels)*.05 and actors > 40, ("Missing rendered terrain or actors", grass, actors)


if __name__ == "__main__":
    assert not rpc("status")["player"]["live"], "Start from the smoke client's menu"
    before = {x["name"] for x in rpc("replay.list")}
    rpc("client.connect", {"address": "127.0.0.1:25587", "recording": True})
    player = wait("player.state", lambda s: s["live"] and s["screen"] == "none")
    for i, name in enumerate(["PortAlice", "PortBob"]):
        rpc("actor.spawn", {"actorId": name, "name": name, "x": player["x"]+2+i*2, "y": player["y"], "z": player["z"]+3, "yaw": 180, "pitch": 0})
    actors = wait("actor.list", lambda a: len(a) == 2 and all(x["state"] == "ready" for x in a))
    assert all(a["recording"] for a in actors)
    rpc("recording.marker", {"name": "port-smoke-start"})
    rpc("actor.scene", {"scripts": {name: [{"action": "move", "ticks": 10, "x": player["x"]+4+i*2, "y": player["y"], "z": player["z"]+3, "yaw": 90, "pitch": 0}] for i, name in enumerate(["PortAlice", "PortBob"])}})
    actors = wait("actor.list", lambda a: all(x["script"]["state"] == "succeeded" for x in a))
    assert actors[0]["x"] == player["x"]+4 and actors[1]["x"] == player["x"]+6
    rpc("player.look", {"yaw": -55, "pitch": 8, "ticks": 5})
    wait("player.state", lambda s: s["action"]["state"] == "succeeded")
    shot = rpc("capture.live")
    (OUT/"live.png").write_bytes(base64.b64decode(shot["pngBase64"]))
    time.sleep(1)
    rpc("recording.marker", {"name": "port-smoke-end"})
    (OUT/"live-state.json").write_text(json.dumps(rpc("status"), indent=2))
    rpc("client.disconnect")
    files = wait("replay.list", lambda f: any(x["name"] not in before for x in f))
    name = next(x["name"] for x in files if x["name"] not in before)
    print("Finalized recording:", name, flush=True)
    rpc("replay.open", {"name": name})
    rpc("playback.set", {"speed": 0})
    state = rpc("status")
    seek = max(0, state["duration"]-500)
    rpc("playback.seek", {"time": seek})
    entities = wait("entities.list", lambda entities: len([e for e in entities if e.get("name") in ["PortAlice", "PortBob"]]) == 2)
    (OUT/"replay-entities.json").write_text(json.dumps(entities, indent=2))
    (OUT/"replay-state.json").write_text(json.dumps(rpc("status"), indent=2))
    print("Reopened replay and restored both actors at", seek, "ms", flush=True)

    wait("status", lambda s: s["entityTrackerReady"])
    cam = {"x": player["x"]-3, "y": player["y"]+1, "z": player["z"]-4, "yaw": -55, "pitch": 12, "roll": 0}
    rpc("camera.set", cam)
    shot = rpc("capture.start", {"width": 640, "height": 360})
    shot = wait("capture.status", lambda s: s["state"] in ["succeeded", "failed"], {"captureId": shot["captureId"]})
    assert shot["state"] == "succeeded", shot
    (OUT/"replay.png").write_bytes(base64.b64decode(shot["pngBase64"]))
    verify_scene(OUT/"replay.png")
    duration = rpc("status")["duration"]
    rpc("path.clear")
    for path_time, replay_time in [(0, max(0, duration-1800)), (1000, max(0, duration-800))]:
        rpc("path.keyframe", {"type": "time", "time": path_time, "replayTime": replay_time})
        rpc("path.keyframe", {"type": "position", "time": path_time, **cam})
    job = rpc("render.start", {"name": name.removesuffix(".mcpr") + ".smoke.mp4", "width": 320, "height": 180, "fps": 10})
    job = wait("render.status", lambda s: s["state"] in ["succeeded", "failed", "cancelled"], {"jobId": job["jobId"]})
    assert job["state"] == "succeeded" and job["framesDone"] == 10, job
    # A successful encoder alone does not prove that the post-export GUI survived.
    time.sleep(2)
    assert rpc("status")["render"]["state"] == "succeeded"
    verify_scene(OUT.parents[1]/"run/port-smoke/replay_videos"/(name.removesuffix(".mcpr") + ".smoke.mp4"))
    (OUT/"render-state.json").write_text(json.dumps(job, indent=2))
    rpc("replay.close")
    assert not rpc("status")["replayOpen"]
    print("Exported 10 frames; client remained usable and closed the replay", flush=True)
