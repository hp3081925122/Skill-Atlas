package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class PointRule {
    public static final int MAX_TARGETS = 64;

    public String id = "";
    public String name = "";
    public RuleType type = RuleType.KILL;
    public final List<String> targets = new ArrayList<>();
    public int count = 1;
    public int points = 1;
    public String pool = "";
    public int limit;

    public PointRule copy() {
        PointRule r = new PointRule();
        r.id = id;
        r.name = name;
        r.type = type;
        r.targets.addAll(targets);
        r.count = count;
        r.points = points;
        r.pool = pool;
        r.limit = limit;
        return r;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("type", type.key);
        JsonArray t = new JsonArray();
        for (String s : targets) {
            t.add(s);
        }
        o.add("targets", t);
        o.addProperty("count", count);
        o.addProperty("points", points);
        o.addProperty("pool", pool);
        o.addProperty("limit", limit);
        return o;
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

    public static PointRule fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "rule");
        PointRule r = new PointRule();
        r.id = Json.id(o, "id");
        r.name = Json.string(o, "name", "", 128);
        r.type = RuleType.byKey(Json.string(o, "type", RuleType.KILL.key, 32));
        if (r.type.targets != RuleType.Targets.NONE) {
            for (String s : Json.list(o, "targets", MAX_TARGETS, JsonElement::getAsString)) {
                if (!s.isBlank()) {
                    String t = normalizeTarget(r.type, s);
                    if (!r.targets.contains(t)) {
                        r.targets.add(t);
                    }
                }
            }
        }
        r.count = Json.integer(o, "count", 1, 1, 1_000_000_000);
        r.points = Json.integer(o, "points", 1, 1, 1_000_000);
        r.pool = Json.id(o, "pool");
        r.limit = Json.integer(o, "limit", 0, 0, 1_000_000_000);
        return r;
    }
}
