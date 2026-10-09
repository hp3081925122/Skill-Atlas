package com.hp.skillatlas.server;

import com.hp.skillatlas.model.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class AtlasData extends SavedData {
    public final Map<String, PlayerProgress> players = new LinkedHashMap<>();
    public static AtlasData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AtlasData::load, AtlasData::new, "skill_atlas_players");
    }
    public static AtlasData load(CompoundTag tag) {
        AtlasData data = new AtlasData();
        if (tag.contains("players")) {
            String json = tag.contains("players", 7) ? new String(tag.getByteArray("players"), StandardCharsets.UTF_8) : tag.getString("players");
            Map<String, PlayerProgress> saved = Definition.JSON.fromJson(json, new TypeToken<Map<String, PlayerProgress>>() {}.getType());
            if (saved != null) data.players.putAll(saved);
        }
        return data;
    }
    public PlayerProgress player(UUID id) { return players.computeIfAbsent(id.toString(), k -> new PlayerProgress()); }
    @Override public CompoundTag save(CompoundTag tag) { tag.putByteArray("players", Definition.JSON.toJson(players).getBytes(StandardCharsets.UTF_8)); return tag; }
}
