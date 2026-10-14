package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Icon {
    public static final String ITEM = "item";
    public static final String TEXTURE = "texture";

    public String type = ITEM;
    public String id = "minecraft:book";
    public String nbt = "";

    public Icon() {
    }

    public Icon(String type, String id, String nbt) {
        this.type = type;
        this.id = id;
        this.nbt = nbt;
    }

    public static Icon item(String id) {
        return new Icon(ITEM, id, "");
    }

    public boolean isTexture() {
        return TEXTURE.equals(type);
    }

    public Icon copy() {
        return new Icon(type, id, nbt);
    }

    public String key() {
        return type + "|" + id + "|" + nbt;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("id", id);
        if (!nbt.isEmpty()) {
            o.addProperty("nbt", nbt);
        }
        return o;
    }

    public static Icon fromJson(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return new Icon();
        }
        if (e.isJsonPrimitive()) {
            return item(Json.resource(e.getAsString(), "icon"));
        }
        JsonObject o = Json.object(e, "icon");
        String type = Json.string(o, "type", ITEM, 16);
        if (!ITEM.equals(type) && !TEXTURE.equals(type)) {
            throw new AtlasException("skill_atlas.error.bad_icon", type);
        }
        String nbt = Json.string(o, "nbt", "", 4096).trim();
        return new Icon(type, Json.resource(Json.string(o, "id", "minecraft:book", 256), "icon"), nbt);
    }
}
