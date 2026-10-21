# Historical build input

The original, unmodified official ReplayMod 1.7.10-2.5.2 JAR is retained here
because its download host is refusing connections in both local and hosted CI
builds. SHA-256: `e8b0379ed3b03b3028145c71962aa141757844f2eb7b82ce5390a9cee5a8ce96`.
The preparation script verifies this exact hash before using it; the normalized
compile copy remains ignored in `../libs`.

Corresponding upstream source is retained in `source`, including the original
build and mapping inputs, licenses and both submodules:

- ReplayMod/ReplayMod: `ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885` (2.5.2)
- ReplayMod/jGui: `6641836321f4583e6f56d8ceb59da452213396e7`
- ReplayMod/Translations: `63b7a8bea1ab9a9f005c9ae0e1c54b4432cbc9e6`

All original JAR notices are preserved. The original multi-version source uses
GPL-3.0-or-later and its historical preprocessing build; it is reference source,
not a claim that that old toolchain builds today. A direct local attempt failed
to resolve the pinned preprocessor plugin `24ac087`. Rebuilding that plugin
then exposed retired MCPBot metadata and further historical ForgeGradle inputs.
The maintained fork compiles its compatibility replacements and adapter from
source and combines them with this verified base into one runtime JAR. A full
historical source rebuild remains an issue #6 gate; see
[the build design](../../docs/ENGINE_FORK_DESIGN.md).
