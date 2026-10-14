package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ItemCost {
    public static final int MAX_COUNT = 99_999;
    public static final int MAX_NBT = 8192;

    public String item = "minecraft:diamond";
    public int count = 1;
    public String nbt = "";

    public ItemCost() {
    }

    public ItemCost(String item, int count, String nbt) {
        this.item = item;
        this.count = count;
        this.nbt = nbt;
    }

    public ItemCost copy() {
        return new ItemCost(item, count, nbt);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("item", item);
        o.addProperty("count", count);
        if (!nbt.isEmpty()) {
            o.addProperty("nbt", nbt);
        }
        return o;
    }

    public static ItemCost fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "item");
        String id = Json.resource(Json.string(o, "item", "", 256), "item");
        int count = Json.integer(o, "count", 1, 1, MAX_COUNT);
        String nbt = Json.string(o, "nbt", "", MAX_NBT).trim();
        return new ItemCost(id, count, nbt);
    }
}
