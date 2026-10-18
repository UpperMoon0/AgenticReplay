# Record without shaders, export with shaders

Yes: replay recording stores timestamped network/world data, not the rendered gameplay pixels. You can play with the shader pack disabled for better FPS, then enable it when replaying and exporting footage. Offline export renders frames at the chosen output FPS; demanding shaders generally increase export time. Server lag and chunks/entities never recorded cannot be repaired afterward.

## Recommended workflow

1. Keep the same Minecraft/content-mod versions and install a compatible shader loader.
2. Turn the shader pack **off** while playing. Enable recording before joining.
3. Disconnect and let the `.mcpr` finish saving.
4. Open the replay, prepare a camera path, enable the shader pack, and make a short **Default** render test.
5. If the test looks right, export the full footage.

The shader loader does not inherently need to be present when packets are recorded. Keeping it installed and toggling the pack is the simplest low-risk setup. Mod compatibility checks concern recorded content mods and versions, not whether a shader pack was enabled.

## Version boundaries

- **1.7.10:** historical ReplayMod 2.5.2 contains shader timing compatibility. A [historical developer explanation](https://www.replaymod.com/forum/thread/2592) points to OptiFine-integrated shaders over older standalone GLSL/ShadersMod combinations, which had cinematic-hand problems. **This is not a current compatibility guarantee:** the [current official documentation](https://www.replaymod.com/docs/#compatibility-shaders) says pre-1.16 OptiFine support ended and many versions break ReplayMod. Any proposed 1.7.10 shader stack needs an actual runtime/export test. The adapter leaves path editing/export to the original ReplayMod UI.
- **1.20.1:** this fork has Oculus integration and compiles against Oculus 1.7.0 / Embeddium 0.3.30. This is an intended integration, not a guarantee for every shader pack or other rendering mod.
- **ODS/VR:** special ODS rendering uses internal shader paths; modern ODS explicitly replaces the selected shader pack with its internal ODS pack. Do not assume arbitrary cinematic packs work in that mode.

No exact shader-pack/modpack combination has been graphically tested as part of this port. Use the short Default-render test before relying on a long take. General replay/export and loaded-chunk limitations are documented in the [official ReplayMod documentation](https://www.replaymod.com/docs/).
