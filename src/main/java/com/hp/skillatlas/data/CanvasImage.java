package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class CanvasImage {
    public static final int MAX_SIZE = 4096;
    public String id = "";
    public String texture = "";
    public int x;
    public int y;
    public int width = 128;
    public int height = 128;

    public CanvasImage copy() {
        CanvasImage image = new CanvasImage();
        image.id = id;
        image.texture = texture;
        image.x = x;
        image.y = y;
        image.width = width;
        image.height = height;
        return image;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("texture", texture);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("width", width);
        o.addProperty("height", height);
        return o;
    }

    public static CanvasImage fromJson(JsonElement value) {
        JsonObject o = Json.object(value, "image");
        CanvasImage image = new CanvasImage();
        image.id = Json.id(o, "id");
        image.texture = Json.resource(Json.string(o, "texture", "", 256), "texture");
        image.x = Json.integer(o, "x", 0, -SkillNode.MAX_COORD, SkillNode.MAX_COORD);
        image.y = Json.integer(o, "y", 0, -SkillNode.MAX_COORD, SkillNode.MAX_COORD);
        image.width = Json.integer(o, "width", 128, 1, MAX_SIZE);
        image.height = Json.integer(o, "height", 128, 1, MAX_SIZE);
        return image;
    }
}
