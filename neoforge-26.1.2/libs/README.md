# Pinned replay library

`ReplayStudio-replaymod-26.1-2.6.27.jar` contains the ReplayStudio classes,
relocated supporting libraries and ViaVersion protocol data from the official
[ReplayMod 26.1-2.6.27 release](https://modrinth.com/mod/replaymod/version/EJmaQGbB).
It supports Minecraft 26.1.2's protocol 775. The old upstream
`ReplayStudio-master-SNAPSHOT` JAR does not support this protocol.

The extraction deliberately excludes the Fabric mod, its entrypoints, mixins,
Minecraft-facing classes and nested Fabric API dependencies. Minecraft supplies
Netty, Gson, Guava and the core LWJGL runtime.

Reproduce with `python scripts/extract_replaystudio.py`. That script checks the
official release's SHA-512 before extraction and the output's SHA-256 afterwards.
The extracted JAR SHA-256 is
`684910892bb63fff7b57f5f920ac058a9dc8b416089ae51f0101a74c90360a92`.

Sources and licenses:

- [ReplayStudio](https://github.com/ReplayMod/ReplayStudio), LGPL-3.0-or-later.
- [ReplayMod](https://github.com/ReplayMod/ReplayMod), GPL-3.0-or-later.
- [ViaVersion](https://github.com/ReplayMod/ViaVersion), GPL-3.0-or-later.

Minecraft-facing engine sources in the parent module originate from
[ReForgedPlay's 26.1 branch](https://github.com/ferriarnus/ReForgedPlay/tree/3888877fff1b8da1f02a7d2463362cfaaff2b2b7),
commit `3888877fff1b8da1f02a7d2463362cfaaff2b2b7`, with AgenticReplay's API,
recording lifecycle and actor features ported from the maintained Forge lane.
