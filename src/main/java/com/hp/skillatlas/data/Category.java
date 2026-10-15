package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Category {
    public static final int MAX_IMAGES = 64;
    public String id = "";
    public String name = "";
    public Icon icon = Icon.item("minecraft:book");
    public String pool = "";
    public String background = "";
    public boolean tile;
    public final List<CanvasImage> images = new ArrayList<>();

    public Category() {
    }

    public Category(String id, String name, Icon icon, String pool) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.pool = pool;
    }

    public Category copy() {
        Category c = new Category(id, name, icon.copy(), pool);
        c.background = background;
        c.tile = tile;
        images.forEach(image -> c.images.add(image.copy()));
        return c;
    }

    public boolean hasNoPool() {
        return pool.isEmpty();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.add("icon", icon.toJson());
        o.addProperty("pool", pool);
        o.addProperty("background", background);
        o.addProperty("tile", tile);
        JsonArray pictures = new JsonArray();
        images.forEach(image -> pictures.add(image.toJson()));
        o.add("images", pictures);
        return o;
    }

    public static Category fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "category");
        Category c = new Category();
        c.id = Json.id(o, "id");
        c.name = Json.string(o, "name", "", 128);
        c.icon = Icon.fromJson(o.get("icon"));
        c.pool = Json.string(o, "pool", "", 64);
        if (!c.pool.isEmpty() && !Json.validId(c.pool)) {
            throw new AtlasException("skill_atlas.error.bad_id", c.pool);
        }
        String bg = Json.string(o, "background", "", 256).trim();
        if (!bg.isEmpty()) {
            bg = Json.resource(bg, "background");
        }
        c.background = bg;
        c.tile = Json.bool(o, "tile", false);
        Set<String> ids = new HashSet<>();
        for (CanvasImage image : Json.list(o, "images", MAX_IMAGES, CanvasImage::fromJson)) {
            if (!ids.add(image.id)) {
                throw new AtlasException("skill_atlas.error.duplicate", image.id);
            }
            c.images.add(image);
        }
        return c;
    }
}
