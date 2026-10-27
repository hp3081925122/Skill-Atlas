package com.hp.skillatlas.kubejs;

import com.google.gson.*;
import com.hp.skillatlas.AtlasConfig;
import com.hp.skillatlas.data.*;
import com.hp.skillatlas.server.AtlasServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Acquisitions {
    public static final String FIELD = "$acquisitions";
    private static AtlasDefinition indexed;
    private static Map<String, List<String>> index = Map.of();
    private Acquisitions() {}
    public static JsonObject bindings(AtlasDefinition definition) {
        JsonObject root = ExtraData.get(definition);
        if (!root.has(FIELD)) root.add(FIELD, new JsonObject());
        return root.getAsJsonObject(FIELD);
    }
    public static JsonObject binding(AtlasDefinition definition, String pool) {
        JsonObject root = ExtraData.get(definition);
        return root.has(FIELD) && root.getAsJsonObject(FIELD).has(pool) ? root.getAsJsonObject(FIELD).getAsJsonObject(pool) : null;
    }
    public static void remove(AtlasDefinition definition, String pool) {
        JsonObject root = ExtraData.get(definition);
        if (root.has(FIELD)) { root.getAsJsonObject(FIELD).remove(pool); if (root.getAsJsonObject(FIELD).entrySet().isEmpty()) root.remove(FIELD); }
    }
    public static void configure(AtlasDefinition definition, String pool, String type, Map<?, ?> data) {
        JsonObject value = new JsonObject(); value.addProperty("type", ExtraData.typeId(type)); value.add("data", Values.plain(data).getAsJsonObject());
        bounded(value.get("data")); bindings(definition).add(pool, value);
    }
    private static void bounded(JsonElement data) { if (data.toString().getBytes(StandardCharsets.UTF_8).length > 16384) throw new IllegalArgumentException("Acquisition data is too large"); }
    public static void validate(AtlasDefinition definition, TypeRegistry registry) {
        JsonObject root = ExtraData.get(definition);
        if (!root.has(FIELD)) return;
        JsonObject entries = root.getAsJsonObject(FIELD);
        for (var entry : entries.entrySet()) {
            PointPool pool = definition.pools.get(entry.getKey()); JsonObject value = entry.getValue().getAsJsonObject();
            if (pool == null || pool.method != RuleType.PLAYTIME || !pool.targets.isEmpty()) throw new IllegalArgumentException("Invalid custom acquisition pool " + entry.getKey());
            if (!Set.of("type", "data", "name", "description", "unit").containsAll(value.keySet())) throw new IllegalArgumentException("Unknown acquisition declaration field");
            String id = ExtraData.typeId(value.get("type").getAsString()); JsonObject data = value.getAsJsonObject("data"); bounded(data); Values.plain(data);
            if (registry != null) {
                AcquisitionType type = registry.acquisitions.get(id);
                if (type == null || !TypeRegistry.evaluate(() -> type.validator.test(TypeRegistry.data(data)))) throw new IllegalArgumentException("Unknown or invalid acquisition type " + id);
                value.addProperty("name", type.name); value.addProperty("description", type.description); value.addProperty("unit", type.unit);
            }
        }
        if (entries.entrySet().isEmpty()) root.remove(FIELD);
    }
    private static List<String> pools(AtlasDefinition definition, String type) {
        if (indexed != definition) {
            Map<String, List<String>> next = new LinkedHashMap<>(); JsonObject root = ExtraData.get(definition);
            if (root.has(FIELD)) root.getAsJsonObject(FIELD).entrySet().forEach(e -> next.computeIfAbsent(e.getValue().getAsJsonObject().get("type").getAsString(), k -> new ArrayList<>()).add(e.getKey()));
            Map<String, List<String>> frozen = new LinkedHashMap<>(); next.forEach((k, v) -> frozen.put(k, List.copyOf(v))); index = Map.copyOf(frozen); indexed = definition;
        }
        return index.getOrDefault(type, List.of());
    }
    public static Outcome emit(ServerPlayer player, String id, Map<?, ?> input, long units) {
        ExtraData.typeId(id);
        if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        AcquisitionType type = TypeRegistry.current().acquisitions.get(id);
        if (type == null) return Outcome.fail("unknown_acquisition_type");
        if (player instanceof FakePlayer || !AtlasConfig.COUNT_CREATIVE.get() && (player.isCreative() || player.isSpectator())) return Outcome.fail("ineligible_player");
        List<String> matched = new ArrayList<>(); Map<String, Object> payload;
        var definition = AtlasServer.definition();
        try {
            JsonObject json = Values.plain(input).getAsJsonObject(); bounded(json); payload = TypeRegistry.data(json);
            for (String pool : pools(definition, id)) {
                Map<String, Object> data = TypeRegistry.data(binding(definition, pool).getAsJsonObject("data"));
                if (TypeRegistry.evaluate(() -> type.test.test(new AcquisitionContext(player, id, pool, payload, units), data))) matched.add(pool);
            }
        } catch (Throwable error) {
            LoggerFactory.getLogger("skill_atlas_kubejs").error("Custom acquisition match failed for player={} type={}", player.getUUID(), id, error);
            return Outcome.fail("script_error");
        }
        Outcome result = Outcome.ok(); long total = 0; List<Map<String, Object>> details = new ArrayList<>();
        for (String pool : matched) {
            Outcome paid = Hooks.progress(player, definition.pools.get(pool), units, "acquire", id, payload);
            details.add(Map.of("poolId", pool, "ok", paid.ok, "code", paid.code, "actualDelta", paid.actualDelta)); total += paid.actualDelta;
            if (!paid.ok) result = Outcome.fail("partial_failure");
        }
        result.actualDelta = total; result.detail = List.copyOf(details); return result;
    }
    public static void clear() { indexed = null; index = Map.of(); }
}
