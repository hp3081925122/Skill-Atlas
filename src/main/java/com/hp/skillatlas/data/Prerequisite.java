package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Prerequisite {
    public String node;
    public boolean line = true;
    public int level;

    public Prerequisite(String node, boolean line) {
        this(node, line, 0);
    }

    public Prerequisite(String node, boolean line, int level) {
        this.node = node;
        this.line = line;
        this.level = level;
    }

    public boolean met(PlayerProgress progress) {
        return progress.level(node) > level;
    }

    public Prerequisite copy() {
        return new Prerequisite(node, line, level);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("node", node);
        o.addProperty("line", line);
        o.addProperty("level", level);
        return o;
    }

    public static Prerequisite fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive()) {
            String id = e.getAsString();
            if (!Json.validId(id)) {
                throw new AtlasException("skill_atlas.error.bad_id", id);
            }
            return new Prerequisite(id, true);
        }
        JsonObject o = Json.object(e, "prerequisite");
        return new Prerequisite(Json.id(o, "node"), Json.bool(o, "line", true), Json.integer(o, "level", 0, 0, SkillNode.MAX_LEVELS - 1));
    }
}
