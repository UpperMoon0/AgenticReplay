# Pinned historical replay library

`ReplayStudio-master-SNAPSHOT-slim.jar` is the exact binary imported with
ReForgedPlay's 1.21 branch at `a8dfcdce1947ac2fb73375d476710a6500b1ada5`.
SHA-256: `e034b0a39cc906ba38c401a13c09bf6fca5512ecb658428380994f71b7d48250`.

The build resolves this file directly and verifies its checksum before engine
compilation. It does not resolve a moving remote `master-SNAPSHOT` dependency.
The binary includes 1.21 protocol data. Its upstream ReplayStudio source commit
was not embedded in the historical snapshot and has not been established;
tracing that source revision remains an explicit provenance gap in issue #6.

ReplayStudio and its relocated dependencies retain their own notices/source
locations, including [ReplayStudio](https://github.com/ReplayMod/ReplayStudio)
and [ViaVersion](https://github.com/ReplayMod/ViaVersion). Retaining this input
is not a claim that the library has been rebuilt from corresponding source.
