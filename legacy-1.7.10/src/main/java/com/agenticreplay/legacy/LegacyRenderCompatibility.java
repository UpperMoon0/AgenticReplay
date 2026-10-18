package com.agenticreplay.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/** Reports evidence limits; presence of a renderer is not a compatibility certification. */
final class LegacyRenderCompatibility {
    private LegacyRenderCompatibility() {}

    static JsonObject current() {
        ModContainer angelica = Loader.instance().getIndexedModList().get("angelica");
        return describe(angelica == null ? null : angelica.getVersion());
    }

    static JsonObject describe(String angelicaVersion) {
        JsonObject result = new JsonObject();
        result.addProperty("remoteExportAvailable", false);
        result.addProperty("shaderExportVerified", false);
        result.addProperty("angelicaLoaded", angelicaVersion != null);
        JsonArray warnings = new JsonArray();
        warnings.add(new JsonPrimitive("Graphical recording, replay and export have not been runtime-validated for this adapter."));
        if (angelicaVersion != null) {
            result.addProperty("angelicaVersion", angelicaVersion);
            result.addProperty("angelicaSourceInspectedVersion", "2.2.28");
            warnings.add(new JsonPrimitive("Angelica 2.2.28 shader animation uses a wall clock that ReplayMod 2.5.2 does not synchronize to export time."));
            warnings.add(new JsonPrimitive("Angelica 2.2.28 replaces the chunk update path used by ReplayMod's render-all-chunks barrier; incomplete terrain during export is possible."));
            warnings.add(new JsonPrimitive("Angelica 2.2.28 uses a separate hand-render event that ReplayMod 2.5.2 does not cancel; a first-person hand may appear in free-camera footage."));
            warnings.add(new JsonPrimitive("These source findings are not a runtime result; other Angelica versions require separate validation. Do not install OptiFine alongside Angelica."));
        }
        result.add("warnings", warnings);
        return result;
    }
}
