package com.hp.skillatlas;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;

public final class StackComponents {
    public static Supplier<HolderLookup.Provider> clientRegistries = () -> RegistryAccess.EMPTY;
    private static HolderLookup.Provider patchRegistries;
    private static final Map<String, DataComponentPatch> PATCHES = new HashMap<>();

    private StackComponents() {
    }

    public static HolderLookup.Provider registries() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? clientRegistries.get() : server.registryAccess();
    }

    public static CompoundTag tag(ItemStack stack) {
        return (CompoundTag) DataComponentPatch.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries()), stack.getComponentsPatch()).getOrThrow();
    }

    public static synchronized DataComponentPatch patch(String value) {
        var registries = registries();
        if (registries != patchRegistries || PATCHES.size() > 1024) {
            PATCHES.clear();
            patchRegistries = registries;
        }
        DataComponentPatch cached = PATCHES.get(value);
        if (cached != null) {
            return cached;
        }
        try {
            DataComponentPatch patch = DataComponentPatch.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), TagParser.parseCompoundFully(value)).getOrThrow();
            PATCHES.put(value, patch);
            return patch;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public static void apply(ItemStack stack, String value) {
        stack.applyComponentsAndValidate(patch(value));
    }
}
