# Pinned historical replay library

`ReplayStudio-master-SNAPSHOT-slim.jar` is the exact binary imported with
ReForgedPlay's 1.21 branch at `a8dfcdce1947ac2fb73375d476710a6500b1ada5`.
SHA-256: `e034b0a39cc906ba38c401a13c09bf6fca5512ecb658428380994f71b7d48250`.

The build resolves this file directly and verifies its checksum before engine
compilation. It does not resolve a moving remote `master-SNAPSHOT` dependency.
The binary includes 1.21 protocol data. Its ReplayStudio source revision is
`1e96fda6052562477a54a7ced778f25a547f40ae`. A fresh build of that revision
produces all **235 ReplayStudio classes byte-for-byte identically**, and its
15 upstream tests pass. This establishes the source identity independently of
the snapshot filename or ZIP timestamps.

Corresponding source and the unmodified upstream Gradle 8.8 build are retained
in `../library-sources/ReplayStudio-1e96fda.zip`, SHA-256
`aea6fb645f6c1cfea9f7437ac20a8b5421ea05a6cfbeb4d7ae6c6e2eb1a22a39`.
Extract that archive to an empty directory and run `./gradlew jar test` with
Java 21 and a Java 8 toolchain available. Then run:

```sh
python3 scripts/check_replaystudio_provenance.py --rebuilt /path/to/ReplayStudio/build/libs/ReplayStudio-master-SNAPSHOT.jar
```

The upstream build pins its relocated ViaVersion source to
`ff097697873789b06a1954f040814ec808367ccd` in ReplayMod/ViaVersion. Other
bundled dependencies and relocations are declared in that retained build.
The comparison covers ReplayStudio's own classes; it does not claim that the
entire shaded ZIP is identical. Nine downgraded ViaVersion nested classes
differ in the fresh build, so the original verified runtime input is retained.

ReplayStudio and its relocated dependencies retain their own notices/source
locations, including [ReplayStudio](https://github.com/ReplayMod/ReplayStudio)
and [ViaVersion](https://github.com/ReplayMod/ViaVersion). Retaining this input
is not a claim that the library has been rebuilt from corresponding source.
