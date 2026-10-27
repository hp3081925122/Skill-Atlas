package com.hp.skillatlas.kubejs;

import com.google.gson.*;
import com.hp.skillatlas.data.Json;
import java.util.*;

public final class Values {
    private static final Gson GSON = new Gson();
    private Values() {}

    public static long integer(Number value, long min, long max) {
        if (value == null) throw new IllegalArgumentException("A number is required");
        double d = value.doubleValue();
        if (!Double.isFinite(d) || d != Math.rint(d) || d < min || d > max)
            throw new IllegalArgumentException("Integer outside range " + min + ".." + max);
        return value.longValue();
    }

    public static String id(String id) {
        if (!Json.validId(id)) throw new IllegalArgumentException("Invalid atlas ID: " + id);
        return id;
    }

    public static JsonObject json(Map<?, ?> values) {
        return GSON.toJsonTree(values).getAsJsonObject();
    }

    public static JsonElement plain(Object value) { return plain(value, 0, new IdentityHashMap<>(), new int[1]); }
    private static JsonElement plain(Object value, int depth, IdentityHashMap<Object, Boolean> path, int[] count) {
        if (depth > 32 || ++count[0] > 8192) throw new IllegalArgumentException("Script data is too deeply nested or complex");
        if (value == null || value instanceof JsonNull) return JsonNull.INSTANCE;
        if (value instanceof JsonPrimitive p) return plain(p.isBoolean() ? p.getAsBoolean() : p.isNumber() ? p.getAsNumber() : p.getAsString(), depth, path, count);
        if (value instanceof CharSequence s) return new JsonPrimitive(s.toString());
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) {
            if (!Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Script data numbers must be finite");
            return new JsonPrimitive(n);
        }
        if (path.put(value, true) != null) throw new IllegalArgumentException("Cyclic script data is not supported");
        try {
            JsonObject object = new JsonObject();
            if (value instanceof Map<?, ?> map) {
                for (var e : map.entrySet()) {
                    if (!(e.getKey() instanceof CharSequence)) throw new IllegalArgumentException("Script object keys must be strings");
                    object.add(e.getKey().toString(), plain(e.getValue(), depth + 1, path, count));
                }
                return object;
            }
            if (value instanceof JsonObject source) {
                for (var e : source.entrySet()) object.add(e.getKey(), plain(e.getValue(), depth + 1, path, count));
                return object;
            }
            JsonArray array = new JsonArray();
            if (value instanceof List<?> list) { for (Object e : list) array.add(plain(e, depth + 1, path, count)); return array; }
            if (value instanceof JsonArray source) { for (JsonElement e : source) array.add(plain(e, depth + 1, path, count)); return array; }
            throw new IllegalArgumentException("Only ordinary JSON values are allowed in script data");
        } finally { path.remove(value); }
    }

    public static Object snapshot(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            value.getAsJsonObject().entrySet().forEach(e -> result.put(e.getKey(), snapshot(e.getValue())));
            return Collections.unmodifiableMap(result);
        }
        if (value.isJsonArray()) {
            List<Object> result = new ArrayList<>();
            value.getAsJsonArray().forEach(e -> result.add(snapshot(e)));
            return Collections.unmodifiableList(result);
        }
        JsonPrimitive p = value.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsNumber();
        return p.getAsString();
    }
}
