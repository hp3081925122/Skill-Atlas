package com.hp.skillatlas;

import net.minecraftforge.fml.loading.LoadingModList;

public final class KubeJSSupport {
    public static final String LEGACY_ADDON = "skill_atlas_kubejs";
    private static Boolean active;

    private KubeJSSupport() {
    }

    public static boolean active() {
        if (active == null) {
            LoadingModList mods = LoadingModList.get();
            active = mods != null && mods.getModFileById("kubejs") != null && mods.getModFileById(LEGACY_ADDON) == null;
        }
        return active;
    }

    public static boolean legacyAddon() {
        LoadingModList mods = LoadingModList.get();
        return mods != null && mods.getModFileById(LEGACY_ADDON) != null;
    }
}
