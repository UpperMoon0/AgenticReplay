"""Validate an isolated legacy client and native probe against a loopback fixture.

Build the probe with :reobfNativeProbeJar. It is a separate test mod and is never
part of the production JAR. Both HTTP endpoints need the same test token.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    fixture = parser.add_mutually_exclusive_group(required=True)
    fixture.add_argument('--server', help='Disposable 127.0.0.1:port server')
    fixture.add_argument('--singleplayer', action='store_true', help='Create a fresh integrated flat world through the separate probe')
    fixture.add_argument('--resume-live', action='store_true', help='Continue a test-owned live fixture after slow initial world loading')
    parser.add_argument('--url', default='http://127.0.0.1:18771/rpc')
    parser.add_argument('--probe-url', default='http://127.0.0.1:18773/rpc')
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--out', type=Path, default=Path('legacy-1.7.10/build/native-smoke'))
    args = parser.parse_args()
    if args.server and args.server.rsplit(':', 1)[0] not in {'127.0.0.1', 'localhost'}:
        parser.error('Use a disposable loopback server')
    token = os.environ.get('AGENTICREPLAY_SMOKE_TOKEN')
    if not token or len(token) < 32:
        parser.error('Set AGENTICREPLAY_SMOKE_TOKEN to the isolated client/probe token')
    args.out.mkdir(parents=True, exist_ok=True)

    def rpc(method, params=None, probe=False):
        request = urllib.request.Request(args.probe_url if probe else args.url,
                data=json.dumps({'method': method, 'params': params or {}}).encode(),
                headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
        try:
            response = urllib.request.urlopen(request, timeout=40)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            result = json.load(response)
        if not result.get('ok'):
            raise RuntimeError(method + ': ' + str(result))
        return result['result']

    def wait(method, predicate, params=None, seconds=90):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                result = rpc(method, params)
            except RuntimeError as failure:
                # These waits only poll read-only state. World initialization can
                # take longer than one HTTP deadline on the full GTNH profile.
                if method in {'player.state', 'status', 'replay.list'} and 'Client operation timed out' in str(failure):
                    continue
                raise
            if predicate(result):
                return result
            time.sleep(.5)
        raise TimeoutError(method + ': ' + str(result))

    result = {'passed': False, 'server': args.server}
    try:
        result['capabilities'] = rpc('capabilities')
        assert result['capabilities']['engineBuild']['externalReplayEngineRequired'] is False
        initial = rpc('status')
        assert not initial['replayOpen'] and initial['live'] == args.resume_live, 'Start at the menu, or explicitly resume the test-owned live fixture'
        before = {row['name'] for row in rpc('replay.list')}
        if args.singleplayer:
            result['world'] = rpc('world.create', probe=True)
        elif not args.resume_live:
            rpc('client.connect', {'address': args.server, 'recording': True})
        live = wait('player.state', lambda state: state['live'] and state['screen'] == 'none', seconds=300 if args.singleplayer else 90)
        result['live'] = live
        rpc('player.look', {'yaw': 0, 'pitch': 20})
        rpc('recording.marker', {'name': 'native-fork-start'})
        time.sleep(5)
        rpc('recording.marker', {'name': 'native-fork-end'})
        rpc('client.disconnect')
        files = wait('replay.list', lambda rows: any(row['name'] not in before for row in rows))
        name = next(row['name'] for row in files if row['name'] not in before)
        result['recording'] = name
        print('Finalized ' + name, flush=True)
        rpc('replay.open', {'name': name})
        rpc('playback.set', {'speed': 0})
        state = wait('status', lambda state: state.get('cameraReady') and not state.get('seekInProgress'))
        rpc('playback.seek', {'time': max(0, state['duration'] - 1200)})
        wait('status', lambda state: not state.get('seekInProgress'))
        rpc('playback.set', {'speed': 1})
        time.sleep(.5)
        rpc('playback.set', {'speed': 0})
        rpc('camera.set', {'x': live['x'], 'y': live['y'] + 2, 'z': live['z'] - 4,
                           'yaw': 0, 'pitch': 20, 'roll': 0})
        result['replayState'] = rpc('status')
        result['markers'] = rpc('markers.list')
        assert any(marker['name'] == 'native-fork-start' for marker in result['markers'])
        time.sleep(1.5)  # Let the paused, stationary baseline finish drawing.
        result['scene'] = rpc('capture', probe=True)
        baseline = subprocess.check_output([args.ffmpeg, '-v', 'error', '-i', result['scene']['file'],
                '-frames:v', '1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'])
        baseline_colors = list(zip(baseline[0::3], baseline[1::3], baseline[2::3]))
        baseline_terrain = sum(g > r * 1.1 and g > b * 1.3 for r, g, b in baseline_colors) / len(baseline_colors)
        result['baselineTerrainFraction'] = baseline_terrain
        result['export'] = rpc('export', probe=True)
        export = result['export']
        assert export['success'] and export['frames'] == 10 and export['bytes'] > 0, export
        pixels = subprocess.check_output([args.ffmpeg, '-v', 'error', '-i', export['file'],
                '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'])
        frame_size = 320 * 180 * 3
        assert len(pixels) == frame_size * 10, 'Expected ten complete 320x180 frames'
        result['frames'] = []
        for index in range(10):
            data = pixels[index * frame_size:(index + 1) * frame_size]
            colors = list(zip(data[0::3], data[1::3], data[2::3]))
            terrain = sum(g > r * 1.1 and g > b * 1.3 for r, g, b in colors)
            metrics = {'index': index, 'terrainPixels': terrain, 'uniqueColors': len(set(colors))}
            result['frames'].append(metrics)
            assert terrain / len(colors) >= max(.05, baseline_terrain * .9) and metrics['uniqueColors'] > 100, metrics
        rpc('replay.close')
        result['afterClose'] = rpc('status')
        assert not result['afterClose']['replayOpen']
        result['passed'] = True
        print('All ten exported frames contain terrain; replay closed and client restored', flush=True)
    except Exception as failure:
        result['failure'] = str(failure)
        raise
    finally:
        (args.out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')


if __name__ == '__main__':
    main()
