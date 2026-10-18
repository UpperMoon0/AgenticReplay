#!/usr/bin/env python3
"""Fetch only the hash-pinned official ReplayMod runtime; never redistribute it."""
import hashlib
from pathlib import Path
import urllib.request
import zipfile
import io

URL = 'https://www.replaymod.com/download/download_new.php?version=1.7.10-2.5.2'
SHA256 = 'e8b0379ed3b03b3028145c71962aa141757844f2eb7b82ce5390a9cee5a8ce96'
DEST = Path(__file__).resolve().parents[1] / 'libs' / 'replaymod-1.7.10-2.5.2.jar'


def main():
    if DEST.exists():
        data = DEST.read_bytes()
    else:
        with urllib.request.urlopen(URL, timeout=90) as response:
            data = response.read(32 * 1024 * 1024 + 1)
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit('ReplayMod checksum mismatch; refusing unverified dependency')
    DEST.parent.mkdir(parents=True, exist_ok=True)
    if not DEST.exists():
        temporary = DEST.with_suffix('.tmp')
        temporary.write_bytes(data)
        temporary.replace(DEST)
    # Upstream has a duplicate assets/ directory entry. RFG rejects duplicate ZIP
    # entries, so produce a local compile-only copy without directory records.
    # Every class/resource byte and its ZipInfo metadata is preserved unchanged.
    compiled = DEST.with_name('replaymod-1.7.10-2.5.2-compile.jar')
    temporary = compiled.with_suffix('.tmp')
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(temporary, 'w') as target:
        seen = set()
        for entry in source.infolist():
            if entry.is_dir():
                continue
            if entry.filename in seen:
                raise SystemExit('Duplicate non-directory entry in pinned dependency')
            seen.add(entry.filename)
            target.writestr(entry, source.read(entry))
    temporary.replace(compiled)
    print('Verified ReplayMod 1.7.10-2.5.2:', SHA256)


if __name__ == '__main__':
    main()
