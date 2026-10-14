package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Prerequisite {
    public String node;
    public boolean line = true;

    public Prerequisite(String node, boolean line) {
        this.node = node;
        this.line = line;
    }

    public Prerequisite copy() {
        return new Prerequisite(node, line);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("node", node);
        o.addProperty("line", line);
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
        return new Prerequisite(Json.id(o, "node"), Json.bool(o, "line", true));
    }
}
