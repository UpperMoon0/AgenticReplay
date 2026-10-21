# Runtime validation for the engine fork change

Builds and regressions are recorded separately from native client results.
This file must not imply that source adoption establishes rendering parity.

| Lane | Native validation for 0.8.0 |
| --- | --- |
| Forge 1.7.10 / GTNH 2.9.0-RC-2 | In progress in an isolated local profile retaining Galacticraft 3.4.34-GTNH, Angelica 2.2.28 and UniMixins 0.3.1. No current-run result is claimed yet |
| Forge 1.20.1 | Not rerun for this change; source behavior is retained, with shared contracts/distribution checks run separately |
| NeoForge 1.21.1 | Native startup, recording/replay/path/export UI acceptance is outstanding; compile/package success does not certify it |
| NeoForge 26.1.2 | Not rerun for this change; source behavior is retained, with shared contracts/distribution checks run separately |

The legacy profile uses the rebuilt single fork JAR in place of both old
engine/adapter JARs. The original filming profile and its mod files are not
modified. A disposable loopback vanilla 1.7.10 server is the initial recording
fixture; that tests the GTNH client stack, not a GTNH server handshake or
modded-world content compatibility. Shader export and the known Angelica
clock/terrain/hand gaps remain unverified unless explicitly recorded below.

The complete acceptance sequence is startup; record; disconnect/finalize;
reopen; pause/seek/playback; camera/path interaction; render/export; close and
restore client state. Record the exact mod/shader configuration and tested
artifact hash for every claimed native result.
