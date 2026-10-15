package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.PlayerProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressData extends SavedData {
    private static final String NAME = "skill_atlas_players";

    private final Map<UUID, PlayerProgress> players = new HashMap<>();

    public static ProgressData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ProgressData::new, ProgressData::load), NAME);
    }

    public PlayerProgress of(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerProgress());
    }

    public Map<UUID, PlayerProgress> all() {
        return Collections.unmodifiableMap(players);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        players.forEach((id, p) -> all.put(id.toString(), p.save()));
        tag.put("players", all);
        return tag;
    }

    public static ProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressData data = new ProgressData();
        CompoundTag all = tag.getCompound("players");
        for (String key : all.getAllKeys()) {
            try {
                data.players.put(UUID.fromString(key), PlayerProgress.load(all.getCompound(key)));
            } catch (IllegalArgumentException e) {
                SkillAtlas.LOGGER.warn("Skipped invalid player entry {} in skill atlas data", key);
            }
        }
        return data;
    }
}
