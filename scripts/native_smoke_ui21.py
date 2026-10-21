"""Drive only the separate NeoForge 1.21.1 native fixture, never a product API."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request

p = argparse.ArgumentParser()
p.add_argument('--port', type=int, required=True)
p.add_argument('--server', required=True)
p.add_argument('--video', type=Path, required=True)
p.add_argument('--out', type=Path, required=True)
args = p.parse_args()
assert args.server.startswith('127.0.0.1:')
token = os.environ['AGENTICREPLAY_SMOKE_TOKEN']
assert len(token) >= 32


def rpc(method, **params):
    request = urllib.request.Request(f'http://127.0.0.1:{args.port}/rpc',
        json.dumps({'method': method, 'params': params}).encode(),
        {'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=40) as response:
        result = json.load(response)
    assert result['ok'], result
    return result['result']


def wait(check, seconds=60):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        value = check()
        if value:
            return value
        time.sleep(.25)
    raise AssertionError('Native fixture timed out')


args.out.mkdir(parents=True, exist_ok=True)
result = {'passed': False}
try:
    assert not rpc('status')['live'] and not rpc('status')['replayOpen']
    old = set(rpc('list'))
    rpc('connect', address=args.server)
    wait(lambda: rpc('status')['live'])
    time.sleep(6)
    live = rpc('status')
    rpc('disconnect')
    name = wait(lambda: next(iter(set(rpc('list')) - old), None))
    time.sleep(2)
    rpc('open', name=name)
    wait(lambda: rpc('status')['cameraReady'])
    rpc('play', speed=0)
    target = rpc('status')['duration'] - 1500
    rpc('seek', time=target)
    wait(lambda: not rpc('status')['seeking'])
    before = rpc('status')['time']
    rpc('play', speed=1)
    time.sleep(.5)
    assert rpc('status')['time'] > before
    rpc('play', speed=0)
    rpc('camera', x=live['x'], y=live['y'] + 5, z=live['z'], yaw=0, pitch=35)
    time.sleep(1)
    exported = rpc('export')
    assert exported['success'] and exported['frames'] == 10 and exported['bytes'] > 1000, exported
    data = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(args.video),
                                   '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'])
    size = 320 * 180 * 3
    assert len(data) == size * 10
    coverage = []
    for start in range(0, len(data), size):
        frame = data[start:start + size]
        pixels = list(zip(frame[::3], frame[1::3], frame[2::3]))
        assert len(set(pixels)) > 100
        coverage.append(sum(g > r * 1.1 and g > b * 1.3 for r, g, b in pixels) / len(pixels))
    assert min(coverage) > .1 and min(coverage) >= max(coverage) * .9, coverage
    rpc('close')
    assert not rpc('status')['replayOpen']
    result.update(passed=True, replay=name, export=exported, terrainCoverage=coverage,
                  fixture='vanilla 1.21.1 loopback; Embeddium 1.0.8-beta.367; shader packs disabled')
finally:
    (args.out / 'result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
