"""Check the historical library against its recovered, source-built revision."""
import argparse
import hashlib
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'neoforge-1.21.1/library-sources/ReplayStudio-1e96fda.zip'
LIBRARY = ROOT / 'neoforge-1.21.1/libs/ReplayStudio-master-SNAPSHOT-slim.jar'
SOURCE_SHA256 = 'aea6fb645f6c1cfea9f7437ac20a8b5421ea05a6cfbeb4d7ae6c6e2eb1a22a39'
LIBRARY_SHA256 = 'e034b0a39cc906ba38c401a13c09bf6fca5512ecb658428380994f71b7d48250'
ENGINE_DIGEST = '35d36d52e0e6dc4426f08db287340d262771c097a149975ec96575647ad22996'


def classes(archive):
    return {name: archive.read(name) for name in archive.namelist()
            if name.startswith('com/replaymod/replaystudio/')
            and name.endswith('.class') and '/lib/' not in name}


def check(rebuilt=None):
    for path, expected in [(SOURCE, SOURCE_SHA256), (LIBRARY, LIBRARY_SHA256)]:
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError(f'Provenance input changed: {path.name}')
    with zipfile.ZipFile(LIBRARY) as archive:
        original = classes(archive)
    digest = hashlib.sha256()
    for name, data in sorted(original.items()):
        digest.update(name.encode() + b'\0' + hashlib.sha256(data).digest() + b'\n')
    if len(original) != 235 or digest.hexdigest() != ENGINE_DIGEST:
        raise ValueError('Historical ReplayStudio class inventory changed')
    if rebuilt:
        with zipfile.ZipFile(rebuilt) as archive:
            candidate = classes(archive)
        if candidate != original:
            changed = sorted(name for name in original.keys() | candidate.keys()
                             if original.get(name) != candidate.get(name))
            raise ValueError('Source rebuild differs: ' + ', '.join(changed[:10]))
        print('Verified all 235 ReplayStudio classes against the source rebuild')
    else:
        print('Verified pinned ReplayStudio source archive and historical class inventory')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rebuilt', type=Path, help='JAR built from the retained source archive')
    check(parser.parse_args().rebuilt)
