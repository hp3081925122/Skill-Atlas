package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import com.hp.skillatlas.server.AtlasServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class CustomRewards {
    private record Active(ServerPlayer player, String node, int level, int index, RewardType type, JsonObject data) {}
    private static final Map<UUID, Map<String, Active>> ACTIVE = new LinkedHashMap<>();
    private CustomRewards() {}
    public static void refresh(ServerPlayer player) {
        if (AtlasServer.server() == null) return;
        Map<String, Active> old = ACTIVE.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>());
        String reason = old.values().stream().anyMatch(a -> a.player != player) ? "respawn" : Hooks.operation(player);
        Map<String, Active> next = new LinkedHashMap<>();
        var definition = AtlasServer.definition();
        AtlasServer.progress(player).levels().forEach((id, level) -> {
            var node = definition.nodes.get(id);
            if (node == null || level < 1 || level > node.maxLevel()) return;
            List<JsonObject> entries = ExtraData.entries(definition, id, level, "rewards", false);
            for (int index = 0; index < entries.size(); index++) {
                JsonObject e = entries.get(index); RewardType type = TypeRegistry.current().rewards.get(e.get("type").getAsString());
                if (type == null || type.activate == null) continue;
                Active value = new Active(player, id, level, index, type, e.getAsJsonObject("data"));
                next.put(new RewardContext(player, id, level, index, "refresh").key, value);
            }
        });
        for (String key : new ArrayList<>(old.keySet())) {
            Active a = old.get(key), b = next.get(key);
            if (b == null || a.player != b.player || a.type != b.type || !a.data.equals(b.data)) {
                old.remove(key); invoke(a, false, reason);
            }
        }
        next.forEach((key, value) -> {
            if (!old.containsKey(key)) { old.put(key, value); invoke(value, true, reason); }
        });
        if (old.isEmpty()) ACTIVE.remove(player.getUUID());
    }
    private static void invoke(Active value, boolean activate, String reason) {
        try {
            TypeRegistry.evaluate(() -> {
                var callback = activate ? value.type.activate : value.type.deactivate;
                callback.accept(new RewardContext(value.player, value.node, value.level, value.index, reason), TypeRegistry.data(value.data));
                return null;
            });
        } catch (Throwable error) {
            LoggerFactory.getLogger("skill_atlas_kubejs").error("Custom reward {} failed for player={} node={} rank={}", activate ? "activation" : "deactivation", value.player.getUUID(), value.node, value.level, error);
        }
    }
    public static void learned(ServerPlayer player, String id, int from, int to) {
        for (int level = from + 1; level <= to; level++) {
            List<JsonObject> entries = ExtraData.entries(AtlasServer.definition(), id, level, "rewards", false);
            for (int index = 0; index < entries.size(); index++) {
                JsonObject e = entries.get(index); RewardType type = TypeRegistry.current().rewards.get(e.get("type").getAsString());
                if (type == null || type.learned == null) continue;
                RewardContext context = new RewardContext(player, id, level, index, Hooks.operation(player).equals("command") ? "grant" : "learn");
                try { TypeRegistry.evaluate(() -> { type.learned.accept(context, TypeRegistry.data(e.getAsJsonObject("data"))); return null; }); }
                catch (Throwable error) { LoggerFactory.getLogger("skill_atlas_kubejs").error("Custom learning reward failed for player={} node={} rank={}", player.getUUID(), id, level, error); }
            }
        }
    }
    public static List<Component> descriptions(ServerPlayer player, String id, int level) {
        List<Component> result = new ArrayList<>();
        List<JsonObject> entries = ExtraData.entries(AtlasServer.definition(), id, level, "rewards", false);
        for (int i = 0; i < entries.size(); i++) {
            JsonObject e = entries.get(i); RewardType type = TypeRegistry.current().rewards.get(e.get("type").getAsString());
            if (type == null) throw new IllegalStateException("Unknown reward type " + e.get("type"));
            RewardContext context = new RewardContext(player, id, level, i, "preview");
            result.add(Objects.requireNonNull(TypeRegistry.evaluate(() -> type.description.apply(context, TypeRegistry.data(e.getAsJsonObject("data"))))));
        }
        return List.copyOf(result);
    }
    public static void forget(UUID player, String reason) {
        Map<String, Active> active = ACTIVE.remove(player);
        if (active != null) active.values().forEach(a -> invoke(a, false, reason));
    }
    public static void clear(String reason) { for (UUID player : new ArrayList<>(ACTIVE.keySet())) forget(player, reason); }
}
