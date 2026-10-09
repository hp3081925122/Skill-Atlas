package com.hp.skillatlas.core;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.util.*;

public final class StrictJson {
    private StrictJson() {}
    public static <T> T parse(String text, Class<T> type) {
        if (text == null || text.length() > 8_000_000) throw new IllegalArgumentException("JSON exceeds size limit");
        try {
            JsonReader reader = new JsonReader(new StringReader(text)); reader.setLenient(false);
            JsonElement tree = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
            return com.hp.skillatlas.model.Definition.JSON.fromJson(tree, type);
        } catch (IOException | IllegalStateException ex) { throw new IllegalArgumentException("Invalid JSON: " + ex.getMessage(), ex); }
    }
    private static JsonElement read(JsonReader r, int depth) throws IOException {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting limit exceeded");
        switch (r.peek()) {
            case BEGIN_OBJECT:
                JsonObject o = new JsonObject(); r.beginObject(); Set<String> keys = new HashSet<>();
                while (r.hasNext()) { String key = r.nextName(); if (!keys.add(key)) throw new IllegalArgumentException("Duplicate JSON key: " + key); o.add(key, read(r, depth + 1)); }
                r.endObject(); return o;
            case BEGIN_ARRAY:
                JsonArray a = new JsonArray(); r.beginArray(); while (r.hasNext()) a.add(read(r, depth + 1)); r.endArray(); return a;
            case STRING: return new JsonPrimitive(r.nextString());
            case NUMBER: return new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN: return new JsonPrimitive(r.nextBoolean());
            case NULL: r.nextNull(); return JsonNull.INSTANCE;
            default: throw new IllegalArgumentException("Unexpected JSON token");
        }
    }
}
