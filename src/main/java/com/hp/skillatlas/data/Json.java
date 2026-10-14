package com.hp.skillatlas.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Json {
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Gson COMPACT = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.\\-]{1,64}");
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_.\\-/]+");

    private Json() {
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static boolean validResource(String value) {
        return value != null && value.length() <= 256 && RESOURCE.matcher(value).matches();
    }

    public static String normalizeResource(String value) {
        String v = value.trim();
        if (!v.isEmpty() && v.indexOf(':') < 0) {
            v = "minecraft:" + v;
        }
        return v;
    }

    public static String resource(String value, String field) {
        String v = normalizeResource(value);
        if (!validResource(v)) {
            throw new AtlasException("skill_atlas.error.bad_resource", field, value);
        }
        return v;
    }

    public static String id(JsonObject o, String key) {
        String v = string(o, key, "", 64);
        if (!validId(v)) {
            throw new AtlasException("skill_atlas.error.bad_id", v);
        }
        return v;
    }

    public static String string(JsonObject o, String key, String def, int max) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive()) {
            throw new AtlasException("skill_atlas.error.bad_field", key);
        }
        String s = e.getAsString();
        if (s.length() > max) {
            throw new AtlasException("skill_atlas.error.too_long", key, String.valueOf(max));
        }
        return s;
    }

    public static double number(JsonObject o, String key, double def, double min, double max) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        double d;
        try {
            d = e.getAsDouble();
        } catch (RuntimeException ex) {
            throw new AtlasException("skill_atlas.error.bad_field", key);
        }
        if (Double.isNaN(d) || Double.isInfinite(d) || d < min || d > max) {
            throw new AtlasException("skill_atlas.error.range", key, trim(min), trim(max));
        }
        return d;
    }

    public static int integer(JsonObject o, String key, int def, int min, int max) {
        double d = number(o, key, def, min, max);
        if (d != Math.floor(d)) {
            throw new AtlasException("skill_atlas.error.bad_field", key);
        }
        return (int) d;
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            throw new AtlasException("skill_atlas.error.bad_field", key);
        }
        return e.getAsBoolean();
    }

    public static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return new JsonArray();
        }
        if (!e.isJsonArray()) {
            throw new AtlasException("skill_atlas.error.bad_field", key);
        }
        return e.getAsJsonArray();
    }

    public static JsonObject object(JsonElement e, String key) {
        if (e == null || !e.isJsonObject()) {
            throw new AtlasException("skill_atlas.error.bad_field", key);
        }
        return e.getAsJsonObject();
    }

    public static <T> List<T> list(JsonObject o, String key, int max, Function<JsonElement, T> parser) {
        JsonArray a = array(o, key);
        if (a.size() > max) {
            throw new AtlasException("skill_atlas.error.too_many", key, String.valueOf(max));
        }
        List<T> out = new ArrayList<>(a.size());
        for (JsonElement e : a) {
            out.add(parser.apply(e));
        }
        return out;
    }

    public static String trim(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1.0E15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
