package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

public final class PointPool {
    public String id = "";
    public String name = "";
    public int color = 0xECC873;
    public int initial;

    public PointPool() {
    }

    public PointPool(String id, String name, int color, int initial) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.initial = initial;
    }

    public PointPool copy() {
        return new PointPool(id, name, color, initial);
    }

    public static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    public static int parseColor(String text) {
        String t = text.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            throw new AtlasException("skill_atlas.error.bad_color", text);
        }
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            throw new AtlasException("skill_atlas.error.bad_color", text);
        }
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("color", hex(color));
        o.addProperty("initial", initial);
        return o;
    }

    public static PointPool fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "pool");
        PointPool p = new PointPool();
        p.id = Json.id(o, "id");
        p.name = Json.string(o, "name", "", 128);
        p.color = parseColor(Json.string(o, "color", "#ECC873", 16));
        p.initial = Json.integer(o, "initial", 0, 0, 1_000_000_000);
        return p;
    }
}
