package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.PlayerProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressData extends SavedData {
    private static final String NAME = "skill_atlas_players";
    private static final SavedDataType<ProgressData> TYPE = new SavedDataType<>(SkillAtlas.id(NAME), ProgressData::new, CompoundTag.CODEC.xmap(ProgressData::load, ProgressData::save));

    private final Map<UUID, PlayerProgress> players = new HashMap<>();

    public static ProgressData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerProgress of(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerProgress());
    }

    public Map<UUID, PlayerProgress> all() {
        return Collections.unmodifiableMap(players);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag all = new CompoundTag();
        players.forEach((id, p) -> all.put(id.toString(), p.save()));
        tag.put("players", all);
        return tag;
    }

    public static ProgressData load(CompoundTag tag) {
        ProgressData data = new ProgressData();
        CompoundTag all = tag.getCompoundOrEmpty("players");
        for (String key : all.keySet()) {
            try {
                data.players.put(UUID.fromString(key), PlayerProgress.load(all.getCompoundOrEmpty(key)));
            } catch (IllegalArgumentException e) {
                SkillAtlas.LOGGER.warn("Skipped invalid player entry {} in skill atlas data", key);
            }
        }
        return data;
    }
}
