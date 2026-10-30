package com.replaymod.core;

import cpw.mods.fml.relauncher.CoreModManager;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixins;

/** UniMixins launch profile derived from ReplayMod 2.5.2's LoadingPlugin. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions("com.replaymod.core.asm.")
public final class LoadingPlugin implements IFMLLoadingPlugin {
    private static final String[] CONFIGURATIONS = {
            "mixins.core.replaymod.json", "mixins.recording.replaymod.json",
            "mixins.render.replaymod.json", "mixins.render.blend.replaymod.json",
            "mixins.replay.replaymod.json", "mixins.compat.mapwriter.replaymod.json",
            "mixins.compat.shaders.replaymod.json", "mixins.extras.playeroverview.replaymod.json"
    };

    public LoadingPlugin() {
        // FML discovers core plugins before it instantiates the queued Mixin
        // tweaker. Do not initialize Mixin from the wrong loader at this point.
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            try {
                File file = new File(source.getLocation().toURI());
                if (file.isFile()) {
                    CoreModManager.getLoadedCoremods().remove(file.getName());
                    CoreModManager.getReparseableCoremods().add(file.getName());
                }
            } catch (URISyntaxException failure) {
                throw new IllegalStateException("Cannot discover AgenticReplay engine", failure);
            }
        }
    }

    @Override public String[] getASMTransformerClass() {
        String tracker = "com.replaymod.core.asm.GLStateTrackerTransformer";
        String recorder = "com.replaymod.core.asm.RecordingOrderTransformer";
        String angelica = "com.replaymod.core.asm.AngelicaReplayTransformer";
        String bootstrap = "com.replaymod.core.asm.ReplayBootstrapTransformer";
        String missingEntity = "com.replaymod.core.asm.ReplayMissingEntityTransformer";
        String shaderClock = "com.replaymod.core.asm.AngelicaShaderClockTransformer";
        String senderDrain = "com.replaymod.core.asm.ReplaySenderDrainTransformer";
        String fmlChannel = "com.replaymod.core.asm.ReplayFmlChannelTransformer";
        String hodgepodge = "com.replaymod.core.asm.HodgepodgeReplayTransformer";
        String recovery = "com.replaymod.core.asm.ReplayPostProcessTransformer";
        return Boolean.getBoolean("replaymod.glerrors")
                ? new String[] {"com.replaymod.core.asm.GLErrorTransformer", tracker, recorder, angelica, bootstrap, missingEntity, shaderClock, senderDrain, fmlChannel, hodgepodge, recovery}
                : new String[] {tracker, recorder, angelica, bootstrap, missingEntity, shaderClock, senderDrain, fmlChannel, hodgepodge};
    }

    @Override public void injectData(Map<String, Object> data) {
        // UniMixins' CompatCore documents this as the first core-plugin hook
        // after Mixin initialization. Register the unchanged engine mixins here.
        for (String configuration : CONFIGURATIONS) Mixins.addConfiguration(configuration);
    }

    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public String getAccessTransformerClass() { return null; }
}
