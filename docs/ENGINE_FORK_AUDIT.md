# Engine ownership and installation audit

Supported targets are Forge 1.7.10/1.20.1 and NeoForge 1.21.1/26.1.2.
Each installation uses one version-matched AgenticReplay runtime JAR. Remove
separately installed ReplayMod/ReForgedPlay engines and obsolete adapters.
Minecraft 1.18.2, 1.19.2, 1.20.4, 1.20.6 and 1.21.8 are not supported targets.

| Lane | Engine base and ownership | Required runtime | Capabilities |
| --- | --- | --- | --- |
| Forge 1.7.10 | AgenticReplay fork over ReplayMod 2.5.2; pinned binary base plus source-built compatibility replacements | Forge 10.13.4.1614; UniMixins >=0.3.1 | Recording/replay/camera/path/render UI; limited authenticated agent API. No remote export/path API, scripted actors, sequences or native capture |
| Forge 1.20.1 | Complete in-tree source engine, based on ReForgedPlay `ce84410677028cef435a20e9470f58bbd2f174bb` | Forge >=47.1.3; Java 17 | Retained replay UI and modern agent/actors/capture/export API |
| NeoForge 1.21.1 | Complete in-tree source engine, ReForgedPlay `a8dfcdce1947ac2fb73375d476710a6500b1ada5` plus packaging fixes | NeoForge 21.1.82; Java 21 | Retained original recording/replay/path/render UI; no agent API |
| NeoForge 26.1.2 | Complete in-tree source engine, ReForgedPlay `3888877fff1b8da1f02a7d2463362cfaaff2b2b7` plus AgenticReplay ports/fixes | NeoForge 26.1.2.99; Java 25 | Retained replay UI and modern agent/actors/capture/export API |

FFmpeg and a graphical OpenGL client are required for encoded video export.
The engine fork itself is never a separately downloaded runtime dependency.
Replays contain version-specific packets; this repository does not convert
recordings between supported Minecraft versions. Shader/renderer compatibility
and specific export modes require their own runtime evidence.

The Forge engine base is the parent of the initial 1.20.1 agent integration;
the NeoForge bases are the exact imported upstream branch revisions. See
`engine-provenance.json` and Git history for AgenticReplay changes after them.

## Reproducible inputs and corresponding source

- Legacy base: SHA-256 `e8b0379ed3b03b3028145c71962aa141757844f2eb7b82ce5390a9cee5a8ce96`.
  ReplayMod source revision `ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885`, jGui
  `6641836321f4583e6f56d8ceb59da452213396e7`, translations
  `63b7a8bea1ab9a9f005c9ae0e1c54b4432cbc9e6`. These sources and original build
  inputs are checked in. RFG verifies the normalized compile archive against
  every original class/resource byte, compiles the compatibility replacements,
  reobfuscates them, and assembles the one-JAR fork. Unchanged engine code remains
  the original binary; this is not a full historical source rebuild.
- Forge 1.20.1 ReplayStudio dependency is revision `d9f7c11-slim`.
- NeoForge 1.21.1 uses the checked-in ReplayStudio snapshot imported with its
  engine branch, SHA-256 `e034b0a39cc906ba38c401a13c09bf6fca5512ecb658428380994f71b7d48250`.
  The build uses this exact file, not a moving remote `master-SNAPSHOT`.
  Its source revision is now established as
  `1e96fda6052562477a54a7ced778f25a547f40ae`: all 235 ReplayStudio classes
  match a fresh source build byte-for-byte, and all 15 upstream tests pass.
  The corresponding source and build are retained, with a CI rebuild/comparison;
  see [the library provenance](../neoforge-1.21.1/libs/README.md).
- NeoForge 26.1.2 uses the verified extracted protocol-775 ReplayStudio input;
  see [its release/hash/source provenance](../neoforge-26.1.2/libs/README.md).

Original license/attribution notices are retained. Legacy compatibility code
and its source distribution include the GPL notice; shared/agent code retains
its existing LGPL notice. Modern artifacts include both the inherited source
notice and ReplayMod's GPL notice. Third-party library notices remain applicable.
No vanilla or private reference-repository source is redistributed.

Upstream changes are reviewed against the recorded revision, integrated as
version-local source changes, and validated by the same lane-specific build and
runtime checks. Future ports must document this same information before being
declared supported. The binary-backed legacy build must not be described as full source-build parity.

## Confirmed compatibility contracts

- Forge 1.7.10 `CoreModManager` handles `TweakClass` before mod/core-plugin
  scanning and adds that file to the loaded-coremod skip list. `ModDiscoverer`
  skips that list. The UniMixins legacy launch profile removes that cascaded
  tweaker, retains the FML core plugin/normal-mod marker, and forces discovery.
- The user's MC-Modding-Src 1.7.10 `ItemRenderer.renderOverlays` contains the
  original `GL11.glEnable(I)V` call. Galacticraft 3.4.34-GTNH, commit
  `fbdc41b` (`minecraft.ItemRendererMixin`), requires that invocation with
  `require=1`. The source transformer preserves it, invokes GL once, then sends
  tracking notifications and recomputes maximum stack sizes.
- UniMixins 0.3.1, commit `c47090bcae511563f2999ddff3317edc6f23364d`, supplies
  bootstrap for the supported legacy profile. This launch change is not applied
  to modern artifacts. The fork removes embedded Mixin runtime classes/providers and defers all eight
  engine configurations to injectData, after UniMixins initialization. Package
  documentation and license notices are retained. Installed UniMixins is required.
- Angelica 2.2.28 source is `0a4d48964edc7a20c0a93c456123860a4a9e16de`.
  Its shader-clock/terrain/hand integration limits remain documented in
  [SHADER_WORKFLOW.md](SHADER_WORKFLOW.md). No other mod JAR is patched.
- Native testing also exposed JarJar 0.3.7-beta's optional `ModSide` branch
  accessing a private Forge field. Its source at
  `1bf1b8f100285e767999373734f39094a14db66c` confirms that access. The fork
  preserves the original engine's absence of `ModSide`; its regression prevents
  reintroducing that incompatible optional manifest field.
- NEI 2.8.155-GTNH, source `c020ecbf1f563876b6113766296c4b8b1c0c0814`,
  requires a contiguous foreground-render / `SIPUSH GL_LIGHTING` / `glEnable`
  sequence. An early native run reproduced failure when the observer inserted
  `DUP` into that untracked lighting sequence. The transformer now leaves known
  constant capabilities other than fog/texture unchanged, matching the upstream
  tracker's actual switch. Dynamic arguments retain once-only capture. No NEI
  or CodeChicken code is modified and core-plugin order is not relied on.
- NeoForge 1.21.1 `CameraPathExporter` directly uses glTF implementation and
  Jackson classes. The previous packaged JAR omitted them; distribution checks
  now require them and the build includes them explicitly.

## Verification and remaining acceptance

CI builds the four supported targets, checks each packaged engine/UI and
discovery metadata, and validates required export libraries. Legacy regressions
use the real Forge annotation parser, real vanilla overlay bytecode, an ASM
verifier, and executable GL/tracker fixtures, including idempotence and stack
growth with live operands. None of these is a native runtime certification.

Native validation results are recorded in `ENGINE_RUNTIME_VALIDATION.md`.
The isolated GTNH client completed startup with 325 mods; recording/finalize/
reopen/seek/playback/export passed in the separate minimal Angelica fixture.
The updated full GTNH integrated-world round trip also passed, including export
and client restoration. Shader-pack export remains unverified; all four retained lanes now have
representative shader-off native round-trip evidence. API parity is not assumed.
Issue #6 remains open for the complete historical source-build gate and
any outstanding native acceptance.
