"""Check distributable classes, including Loom's nested runtime dependencies."""
import io
import sys
import zipfile

REQUIRED = {
    "de/javagl/jgltf/impl/v1/GlTF.class",
    "de/javagl/jgltf/impl/v2/GlTF.class",
    "de/javagl/jgltf/model/io/v2/GltfAssetWriterV2.class",
    "com/fasterxml/jackson/databind/ObjectMapper.class",
    "com/fasterxml/jackson/core/JsonFactory.class",
    "com/fasterxml/jackson/annotation/JsonProperty.class",
    "com/replaymod/agent/ExportEntityMotion.class",
    "com/replaymod/agent/ExportEntityTrack.class",
}


def classes(archive, depth=0):
    names = set(archive.namelist())
    if depth < 2:
        for name in archive.namelist():
            if name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as child:
                    names.update(classes(child, depth + 1))
    return names


for path in sys.argv[1:]:
    with zipfile.ZipFile(path) as archive:
        missing = REQUIRED - classes(archive)
        assert not missing, f"{path}: missing export runtime classes: {sorted(missing)}"
    print(f"Verified export runtime dependencies: {path}")
