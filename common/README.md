# Shared agent API contracts

Both Minecraft targets compile these **same source files**. This is source sharing, not two copied implementations or a second runtime jar to install.

- `ApiHttpTransport`: loopback-only authenticated HTTP, bounded requests/workers, JSON envelopes, client-thread dispatch, cancellation and secret redaction.
- `ApiPaths`: plain filename, extension, traversal and symlink validation.
- `ApiJson`: strict JSON primitives, Unicode blank strings, finite numbers, integer bounds and object validation. Existing permissive scalar parsing in older modern endpoints remains a thin compatibility wrapper; common numeric validation is still reused.

The shared layer has no Minecraft, Forge, ReplayMod engine, rendering or GUI imports. It compiles with Java 8 and Gson 2.2.4, the oldest supported runtime. CI also runs its identical tests with Java 17/Gson 2.10.1. Tests are included in each game target's test suite as well.

```sh
./gradlew -p common test
./gradlew -p common test -PtestJavaVersion=17 -PgsonVersion=2.10.1
```

Version adapters own thread scheduling, connection lifecycle, engine calls, recording controls, camera APIs and capabilities. Those differ materially between Forge 1.7.10 and 1.20.1 and are deliberately not hidden behind a false one-size-fits-all engine abstraction. Modern-only scripted-actor/path/export code is not pulled into the legacy artifact as unused code.

The existing LGPL-3.0 shared code keeps that license (`LICENSE.txt`). The legacy-specific adapter is GPL-3.0-or-later and includes both notices in its binary/source jars; the modern target keeps its existing LGPL license.
