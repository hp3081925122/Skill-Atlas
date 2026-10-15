package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PointPool {
    public static final int MAX_TARGETS = 64;
    public static final int MAX_DESCRIPTION = 1000;

    public String id = "";
    public String name = "";
    public int color = 0xECC873;
    public int initial;
    public RuleType method = RuleType.KILL;
    public final List<String> targets = new ArrayList<>();
    public boolean hostile = true;
    public int count = 10;
    public int points = 1;
    public int limit;
    public String description = "";

    public PointPool() {
    }

    public PointPool(String id, String name, int color, int initial) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.initial = initial;
    }

    public PointPool copy() {
        PointPool p = new PointPool(id, name, color, initial);
        p.method = method;
        p.targets.addAll(targets);
        p.hostile = hostile;
        p.count = count;
        p.points = points;
        p.limit = limit;
        p.description = description;
        return p;
    }

    public boolean hostileOnly() {
        return method == RuleType.KILL && hostile;
    }

    public long counterTarget() {
        return method == RuleType.MOVEMENT ? count * 100L : count;
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

    public static String normalizeTarget(RuleType type, String raw) {
        String s = raw.trim();
        if (s.startsWith("#")) {
            if (!type.acceptsTags()) {
                throw new AtlasException("skill_atlas.error.tag_not_allowed", s);
            }
            return "#" + Json.resource(s.substring(1), "targets");
        }
        return Json.resource(s, "targets");
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("color", hex(color));
        o.addProperty("initial", initial);
        o.addProperty("method", method.key);
        JsonArray t = new JsonArray();
        for (String s : targets) {
            t.add(s);
        }
        o.add("targets", t);
        o.addProperty("hostile", hostile);
        o.addProperty("count", count);
        o.addProperty("points", points);
        o.addProperty("limit", limit);
        o.addProperty("description", description);
        return o;
    }

    public static PointPool fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "pool");
        PointPool p = new PointPool();
        p.id = Json.id(o, "id");
        p.name = Json.string(o, "name", "", 128);
        p.color = parseColor(Json.string(o, "color", "#ECC873", 16));
        p.initial = Json.integer(o, "initial", 0, 0, 1_000_000_000);
        p.method = RuleType.byKey(Json.string(o, "method", RuleType.KILL.key, 32));
        if (p.method.targets != RuleType.Targets.NONE) {
            for (String s : Json.list(o, "targets", MAX_TARGETS, JsonElement::getAsString)) {
                if (!s.isBlank()) {
                    String t = normalizeTarget(p.method, s);
                    if (!p.targets.contains(t)) {
                        p.targets.add(t);
                    }
                }
            }
        }
        p.hostile = Json.bool(o, "hostile", true);
        p.count = Json.integer(o, "count", 10, 1, 1_000_000_000);
        p.points = Json.integer(o, "points", 1, 1, 1_000_000);
        p.limit = Json.integer(o, "limit", 0, 0, 1_000_000_000);
        p.description = Json.string(o, "description", "", MAX_DESCRIPTION);
        return p;
    }
}
