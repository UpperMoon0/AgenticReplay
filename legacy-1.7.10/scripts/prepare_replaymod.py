#!/usr/bin/env python3
"""Fetch only the hash-pinned official ReplayMod runtime; never redistribute it."""
import hashlib
from pathlib import Path
import urllib.request

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
    print('Verified ReplayMod 1.7.10-2.5.2:', SHA256)


if __name__ == '__main__':
    main()
