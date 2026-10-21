# Maintained replay engine forks

Decision for issue #6: keep engine code version-local, retain the existing
ReplayMod UI, and distribute one AgenticReplay mod JAR per supported lane.
Share the Java 8 API contracts and modern control/planning code only where the
Minecraft contracts agree. No replay protocol conversion or new API parity is
implied by adopting an engine fork.

## Existing source engines

Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2 retain their in-tree engine
implementations. Their builds must compile those sources and package their
runtime libraries. They must never depend on a separately installed ReplayMod
engine. NeoForge 1.21.1 retains its original UI-only capabilities; the other two
modern lanes retain the authenticated agent controls, actors and export API.

## Legacy migration

The initial 1.7.10 migration uses the verified official 2.5.2 build input plus
Java 8 source replacements compiled and reobfuscated by RetroFuturaGradle.
The repository owns the resulting fork artifact and its compatibility changes;
users install that single artifact and remove both old adapter/engine JARs.
The original source, mapping inputs, jGui and translation snapshots and notices
remain in `legacy-1.7.10/engine-inputs`.

This is explicitly a **binary-backed source-overlay build**, not a complete
source rebuild of all historical engine classes. The original Gradle 5.4.1
build cannot resolve its pinned `preprocessor:24ac087` artifact in the tested
environment. Rebuilding that plugin from its source progressed further, but
then encountered the retired MCPBot metadata host and an unavailable historical
ForgeGradle `5bb7a53` dependency in the cross-version preprocessing chain.
Restoring the complete historical source toolchain remains an
issue #6 acceptance gate; the overlay must not be represented as satisfying it.
All replacement classes must be compiled from checked-in source, replace their
whole original class family, and be tested against the exact pinned input.
Unchanged class/resource bytes must retain their upstream provenance.

The legacy artifact targets Forge 1.7.10 with **UniMixins 0.3.1 or later**,
including the affected GTNH stack. Its manifest removes ReplayMod's cascaded
tweaker, retains its FML core plugin/access transformer, marks the core plugin
as containing regular mods, and sets `ForceLoadAsMod`. Bootstrap comes from the
installed UniMixins runtime. This launch profile is legacy-specific and is not
copied into modern artifacts.

The GL transformer preserves the actual `GL11.glEnable/glDisable(I)V` calls.
It duplicates the capability argument, executes GL once, then notifies the
state tracker once. Maximum stack sizes are recomputed. The tracker retains
its old hook ABI for already-transformed callers, and the transformer must be
idempotent. Galacticraft's required `renderOverlays` injection target therefore
remains present. Angelica and Galacticraft are not patched or removed.
Known constant capabilities that the upstream tracker does not observe remain
byte-for-byte untouched, including NEI's contiguous lighting instruction needle.
Dynamic capabilities remain observed without reevaluating their expressions.

## Validation and promotion

Every lane must build in CI and pass distribution/identity/dependency checks.
Legacy additionally needs actual mod-discovery and transformed-bytecode tests.
Build success does not certify a native recording/replay/export cycle.
Record startup, recording/finalize/reopen/seek/playback and rendering results
separately for each tested client and retain explicit unverified capabilities.
The known Angelica shader-clock, terrain-completion and hand-render gaps remain
documented until independently fixed and tested.

Future ports must declare their engine source/revision, reproducible inputs,
runtime dependencies, licenses and capability limits before being called
supported. Upstream updates are reviewed against the pinned revision, applied
as source changes and validated per lane, with the provenance record updated.
