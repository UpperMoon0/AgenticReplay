"""Reproduce the pinned protocol-775 library from the official ReplayMod release."""
import hashlib
import io
from pathlib import Path
import urllib.request
import zipfile

URL = "https://cdn.modrinth.com/data/Nv2fQJo5/versions/EJmaQGbB/replaymod-26.1-2.6.27.jar"
SHA512 = "fef677d78a982e42e9d107cf5566512f43520f4d7c29e206c0256d8508891cf5e2cedc2ba99ab469a52ebd96c8cfa544a8e4398ae4776ba44693feededb9f8f1"
OUTPUT_SHA256 = "684910892bb63fff7b57f5f920ac058a9dc8b416089ae51f0101a74c90360a92"
PREFIXES = ("com/replaymod/replaystudio/", "com/replaymod/lib/", "assets/viaversion/")


def extract(data):
    if hashlib.sha512(data).hexdigest() != SHA512:
        raise ValueError("Official release checksum mismatch")
    output = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
        for name in sorted(source.namelist()):
            if name.startswith(PREFIXES) and not name.endswith("/"):
                entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                target.writestr(entry, source.read(name))
    result = output.getvalue()
    if hashlib.sha256(result).hexdigest() != OUTPUT_SHA256:
        raise ValueError("Extracted library checksum mismatch")
    return result


if __name__ == "__main__":
    destination = Path(__file__).resolve().parents[1] / "neoforge-26.1.2/libs/ReplayStudio-replaymod-26.1-2.6.27.jar"
    with urllib.request.urlopen(URL, timeout=60) as response:
        result = extract(response.read())
    destination.write_bytes(result)
    print(f"Verified {destination.name}: {OUTPUT_SHA256}")
