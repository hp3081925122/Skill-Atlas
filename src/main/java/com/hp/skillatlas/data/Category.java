package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Category {
    public String id = "";
    public String name = "";
    public Icon icon = Icon.item("minecraft:book");
    public String pool = "";

    public Category() {
    }

    public Category(String id, String name, Icon icon, String pool) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.pool = pool;
    }

    public Category copy() {
        return new Category(id, name, icon.copy(), pool);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.add("icon", icon.toJson());
        o.addProperty("pool", pool);
        return o;
    }

    public static Category fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "category");
        Category c = new Category();
        c.id = Json.id(o, "id");
        c.name = Json.string(o, "name", "", 128);
        c.icon = Icon.fromJson(o.get("icon"));
        c.pool = Json.id(o, "pool");
        return c;
    }
}
