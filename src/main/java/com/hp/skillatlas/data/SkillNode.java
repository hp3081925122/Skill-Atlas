package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class SkillNode {
    public static final int MAX_LEVELS = 100;
    public static final int MAX_PREREQUISITES = 64;
    public static final int MAX_COORD = 1_000_000;
    public static final double MIN_SIZE = 0.5D;
    public static final double MAX_SIZE = 4.0D;

    public String id = "";
    public String category = "";
    public String name = "";
    public String tooltip = "";
    public String aliases = "";
    public Icon icon = new Icon();
    public int x;
    public int y;
    public NodeShape shape = NodeShape.RSQUARE;
    public double size = 1.0D;
    public boolean hidden;
    public boolean requireAll = true;
    public final List<Prerequisite> prerequisites = new ArrayList<>();
    public final List<SkillLevel> levels = new ArrayList<>();

    public int maxLevel() {
        return levels.size();
    }

    public Prerequisite prerequisite(String nodeId) {
        for (Prerequisite p : prerequisites) {
            if (p.node.equals(nodeId)) {
                return p;
            }
        }
        return null;
    }

    public SkillNode copy() {
        SkillNode n = new SkillNode();
        n.id = id;
        n.category = category;
        n.name = name;
        n.tooltip = tooltip;
        n.aliases = aliases;
        n.icon = icon.copy();
        n.x = x;
        n.y = y;
        n.shape = shape;
        n.size = size;
        n.hidden = hidden;
        n.requireAll = requireAll;
        for (Prerequisite p : prerequisites) {
            n.prerequisites.add(p.copy());
        }
        for (SkillLevel l : levels) {
            n.levels.add(l.copy());
        }
        return n;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("category", category);
        o.addProperty("name", name);
        o.addProperty("tooltip", tooltip);
        if (!aliases.isEmpty()) {
            o.addProperty("aliases", aliases);
        }
        o.add("icon", icon.toJson());
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("shape", shape.key);
        o.addProperty("size", size);
        o.addProperty("hidden", hidden);
        if (requireAll) {
            o.addProperty("requirement", "all");
        } else {
            o.addProperty("requirement", "any");
        }
        JsonArray pre = new JsonArray();
        for (Prerequisite p : prerequisites) {
            pre.add(p.toJson());
        }
        o.add("prerequisites", pre);
        JsonArray lv = new JsonArray();
        for (SkillLevel l : levels) {
            lv.add(l.toJson());
        }
        o.add("levels", lv);
        return o;
    }

    public static SkillNode fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "node");
        SkillNode n = new SkillNode();
        n.id = Json.id(o, "id");
        n.category = Json.id(o, "category");
        n.name = Json.string(o, "name", "", 128);
        n.tooltip = Json.string(o, "tooltip", "", 4000);
        n.aliases = Json.string(o, "aliases", "", 512);
        n.icon = Icon.fromJson(o.get("icon"));
        n.x = Json.integer(o, "x", 0, -MAX_COORD, MAX_COORD);
        n.y = Json.integer(o, "y", 0, -MAX_COORD, MAX_COORD);
        n.shape = NodeShape.byKey(Json.string(o, "shape", NodeShape.RSQUARE.key, 16));
        n.size = Json.number(o, "size", 1.0D, MIN_SIZE, MAX_SIZE);
        n.hidden = Json.bool(o, "hidden", false);
        String requirement = Json.string(o, "requirement", "all", 8);
        if ("all".equals(requirement)) {
            n.requireAll = true;
        } else if ("any".equals(requirement)) {
            n.requireAll = false;
        } else {
            throw new AtlasException("skill_atlas.error.bad_field", "requirement");
        }
        n.prerequisites.addAll(Json.list(o, "prerequisites", MAX_PREREQUISITES, Prerequisite::fromJson));
        n.levels.addAll(Json.list(o, "levels", MAX_LEVELS, SkillLevel::fromJson));
        if (n.levels.isEmpty()) {
            throw new AtlasException("skill_atlas.error.no_levels", n.id);
        }
        return n;
    }
}
