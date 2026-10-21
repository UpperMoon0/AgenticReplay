# Version branches consolidated into main

The version branches are preserved as separate engine lanes. This integration
does not assert equal APIs, renderer compatibility, or native test coverage.
NeoForge 1.20.4 is removed entirely by request; it is not a build target.

| Source branch | Module | Existing capabilities |
| --- | --- | --- |
| `1.18.2` | `forge-1.18.2` | Original ReplayMod recording/playback/path/render UI |
| `1.19.2` | `forge-1.19.2` | Original ReplayMod recording/playback/path/render UI |
| `1.20.1` and `codex/multiversion-26.1.2` | `forge-1.20.1` | Engine, agent API, actors, capture/export |
| `1.20.6` | `neoforge-1.20.6` | Original ReplayMod recording/playback/path/render UI |
| `1.21` (Minecraft 1.21.1) | `neoforge-1.21.1` | Original ReplayMod recording/playback/path/render UI |
| `1.21.8` | `neoforge-1.21.8` | Existing experimental engine, including syncing work |
| `codex/multiversion-26.1.2` | `neoforge-26.1.2` | Engine, agent API, actors, capture/export |
| `feat/forge-1.7.10` merged in `1.20.1` | `legacy-1.7.10` | Limited agent adapter; external engine until issue #6 |

Feature branches already included in `1.20.1` are preserved through that history.
The local `codex/scripted-actors` branch's 1.20.4 implementation is superseded by
its 1.20.1 port and the current modern modules; the removed 1.20.4 source is not
reintroduced. The integration merge records every version branch head.

`api-common` retains the identical Java 8/Gson 2.2.4 API transport, JSON and file
contracts used by the merged legacy adapter. `common` retains the Java 17 actor,
action and export planning code from the modern multi-version branch. Modern
modules compile both sets of shared source; legacy compiles only `api-common`.
Neither directory is a second runtime mod to install.

Run `./gradlew :forge-1.20.1:build` or
`./gradlew :neoforge-26.1.2:build` for the modern modules. Other lanes have isolated
builds, e.g. `./gradlew -p forge-1.18.2 build`. `buildAll` and `testAllVersions`
include every lane. Prepare the pinned legacy dependency with
`python3 legacy-1.7.10/scripts/prepare_replaymod.py` before building legacy.

Each lane's existing mappings, dependency pins, license, and engine source are
retained. Installation and engine-ownership changes belong to issue #6, after
this baseline integration. Existing replays remain tied to their packet version.
