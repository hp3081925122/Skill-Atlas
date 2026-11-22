package com.hp.skillatlas;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;

public final class KubeJSSupport {
    public static final String LEGACY_ADDON = "skill_atlas_kubejs";
    private static Boolean active;

    private KubeJSSupport() {
    }

    private static LoadingModList mods() {
        FMLLoader loader = FMLLoader.getCurrentOrNull();
        if (loader == null) {
            return null;
        }
        return loader.getLoadingModList();
    }

    public static boolean active() {
        if (active == null) {
            LoadingModList mods = mods();
            active = mods != null && mods.getModFileById("kubejs") != null && mods.getModFileById(LEGACY_ADDON) == null;
        }
        return active;
    }

    public static boolean legacyAddon() {
        LoadingModList mods = mods();
        return mods != null && mods.getModFileById(LEGACY_ADDON) != null;
    }
}
