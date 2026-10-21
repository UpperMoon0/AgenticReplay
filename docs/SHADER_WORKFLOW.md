# Record without shaders, export with shaders

Replay recording stores timestamped network/world data, not the rendered gameplay pixels. This makes recording with a shader pack disabled and enabling it for export possible in principle. Offline export renders frames at the chosen output FPS; demanding shaders generally increase export time. Server lag and chunks/entities never recorded cannot be repaired afterward.

**The maintained fork passed a shader-off recording/replay/export cycle in the full GTNH 2.9.0-RC-2 stack. Shader-pack animation clocks and hand rendering remain unverified; this result does not certify shader export.**

## GTNH uses Angelica

Keep the rendering stack supplied with the intended GTNH instance. Do not add OptiFine alongside Angelica: Angelica explicitly lists it as permanently incompatible. Angelica backports Iris shader functionality and uses its own threaded terrain renderer; the historical ReplayMod OptiFine hooks do not cover those implementations.

The following are source-review baselines checked on 2026-10-18, not instructions to upgrade a working pack or a claim about the user's installed version:

- GTNH **2.8.4** manifest: Angelica **1.0.0-beta66b**, GTNHLib **0.7.10**, UniMixins **0.1.23**, and lwjgl3ify **2.1.16** for Java 9+ distributions. This older Angelica version was not audited in this pass.
- GTNH **2.9.0-RC-2** manifest: Angelica **2.2.28**, GTNHLib **0.11.52**, UniMixins **0.3.1**, and lwjgl3ify **3.0.37** for Java 9+ distributions. This is a release candidate, not an assertion that every GTNH installation uses it.
- The inspected daily manifest lists Angelica **2.2.34**. Newer releases do not inherit a compatibility certification from the 2.2.28 source review.

Authoritative manifests: [GTNH 2.8.4](https://github.com/GTNewHorizons/DreamAssemblerXXL/blob/d745ab8b69776ee9c20fd79daf927706728e506c/releases/manifests/2.8.4.json), [GTNH 2.9.0-RC-2](https://github.com/GTNewHorizons/DreamAssemblerXXL/blob/d745ab8b69776ee9c20fd79daf927706728e506c/releases/manifests/2.9.0-RC-2.json), [daily snapshot](https://github.com/GTNewHorizons/DreamAssemblerXXL/blob/d745ab8b69776ee9c20fd79daf927706728e506c/releases/manifests/daily.json).

## What the source establishes

Reviewed Angelica tag **2.2.28**, commit **0a4d48964edc7a20c0a93c456123860a4a9e16de**, against ReplayMod commit **ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885**. No Angelica-specific ReplayMod integration was found in either reviewed tree.

1. **Shader animation clock is not synchronized.** Angelica calls `SystemTimeUniforms.TIMER.beginFrame(System.nanoTime())` for each world render. Historical ReplayMod's 1.7.10 time correction targets `shadersmod.client.Shaders`, not Angelica's `net.coderbot.iris` timer. Therefore shader effects driven by `frameTime`/`frameTimeCounter` can follow actual render duration instead of the intended replay/export timeline. This is a source-level gap, not a measured visual result. Sources: [Angelica render hook, lines 42–61](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/mixin/java/com/gtnewhorizons/angelica/mixins/early/shaders/MixinEntityRenderer.java#L42-L61), [timer implementation](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/main/java/net/coderbot/iris/uniforms/SystemTimeUniforms.java#L94-L139), [legacy shader hook](https://github.com/ReplayMod/ReplayMod/blob/ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885/src/main/java/com/replaymod/compat/shaders/mixin/MixinShaders.java#L14-L48).

2. **The historical terrain-completion mechanism is bypassed.** ReplayMod sets `renderAllChunks=true` on `RenderGlobal.updateRenderers` during export. Angelica replaces that method, ignores the boolean, processes its main-thread queue for a 5 ms budget and returns true. Its terrain setup separately passes `updateChunksImmediately=false`. Incomplete terrain or pop-in during export is therefore a risk, even with shader packs disabled. Sources: [ReplayMod barrier](https://github.com/ReplayMod/ReplayMod/blob/ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885/src/main/java/com/replaymod/render/mixin/MixinEntityRenderer.java#L76-L84), [Angelica terrain setup](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/mixin/java/com/gtnewhorizons/angelica/mixins/early/celeritas/terrain/MixinRenderGlobal.java#L231-L240), [Angelica update override](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/mixin/java/com/gtnewhorizons/angelica/mixins/early/celeritas/terrain/MixinRenderGlobal.java#L309-L339).

3. **Free-camera hand suppression uses different events.** ReplayMod cancels Forge's `RenderHandEvent` when viewing through its free camera. Angelica's shader hand renderer posts a separate Angelica event. Its additional spectator check tests game mode ID 3, whereas the legacy replay sender uses Adventure mode. A first-person hand in free-camera footage is consequently a risk. Sources: [Angelica hand guard](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/main/java/net/coderbot/iris/pipeline/HandRenderer.java#L77-L86), [Angelica event](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/main/java/com/gtnewhorizons/angelica/event/RenderHandEvent.java), [ReplayMod hand guard](https://github.com/ReplayMod/ReplayMod/blob/ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885/src/main/java/com/replaymod/replay/camera/CameraEntity.java#L751-L758), [legacy replay game mode](https://github.com/ReplayMod/ReplayMod/blob/ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885/src/main/java/com/replaymod/replay/FullReplaySender.java#L675-L683).

The camera/framebuffer path is more promising, but is still untested: ReplayMod invokes the normal world renderer for each exported frame; Angelica captures the camera from `mc.renderViewEntity` there and writes its final pass to the main Minecraft framebuffer's color texture. Angelica also observes framebuffer size changes. These code paths justify a runtime test, not a compatibility guarantee. Sources: [camera update](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/mixin/java/com/gtnewhorizons/angelica/mixins/early/angelica/MixinEntityRenderer.java#L36-L49), [final framebuffer pass](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/src/main/java/net/coderbot/iris/postprocess/FinalPassRenderer.java#L202-L237).

## Current adapter behavior

`capabilities` and `status` expose `renderCompatibility`: the loaded Angelica version, the reviewed source version when Angelica is detected, unverified-export status, and the known source-level warnings. The fork waits for Angelica 2.2.28 graph/build/upload completion during export, advancing the renderer's owning frame stamp between graph searches. A native shader-off test checks terrain coverage in every exported frame. Shader timing, shader hand suppression and other Angelica versions remain unverified.

The maintained 1.7.10 engine fork retains camera-path editing and video export in the original ReplayMod UI. There is no remote shader-toggle or remote export API in this target. The version-gated terrain integration is covered by native export evidence; it does not certify shader-pack behavior.

## Conditional workflow and acceptance test

1. Use a copy of the intended GTNH client profile with its existing dependency versions. Keep Angelica installed and turn the shader **pack** off; do not remove the renderer or substitute OptiFine.
2. Enable recording before joining. Record a short take with moving players/entities, chunk changes, modded machines, water and foliage. Disconnect and wait for the `.mcpr` to finish saving.
3. Verify opening, pausing, seeking, camera movement and reopening with shaders off. A recording success alone does not establish replay compatibility.
4. In the original UI, create a short camera path and first test a **Default** export with shaders off. Include a newly visible chunk boundary to detect missing meshes or pop-in.
5. Enable an Angelica-compatible shader pack, then repeat the export. Check freecam hands, camera position, shadows, animated water/foliage, particles and framebuffer size changes. Compare repeated exports and different output FPS to expose wall-clock-dependent animation. Preserve the actual shader-pack version/settings in the test record.
6. Only rely on full-length captures after the exact profile passes. Keep original replays and record failures with client/export logs and the full mod/version list.

The shader loader does not inherently need to be present when packets are recorded, but keeping the same installed stack and toggling the pack avoids unnecessary differences. Mod compatibility checks concern recorded content mods and versions, not shader on/off state; bypassing a mismatch warning does not make incompatible recordings safe to use.

## Other targets and modes

- **1.20.1:** this fork's Oculus 1.7.0 / Embeddium 0.3.30 integration is separate from Angelica's 1.7.10 classes and cannot be assumed to apply to them.
- **ODS/VR, 360-degree, stereo, depth and alpha:** these require their own validation. In particular, modern ODS replaces the selected shader pack with an internal ODS pack. The initial Angelica acceptance target is an ordinary Default color export on its OpenGL renderer. No Vulkan/Metal/SDL GPU export support is claimed.

General replay/export and loaded-chunk limitations are documented in the [official ReplayMod documentation](https://www.replaymod.com/docs/). [Angelica's pinned README](https://github.com/GTNewHorizons/Angelica/blob/0a4d48964edc7a20c0a93c456123860a4a9e16de/README.MD) documents dependencies, shader targets and incompatible renderers. Historical OptiFine support statements do not certify Angelica.
