package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EditOps {
    public static final int MAX_OPS = 20_000;

    public static final class Changes {
        public final Set<String> nodes = new LinkedHashSet<>();
        public final Set<String> shrunk = new HashSet<>();
        public final Set<String> categories = new HashSet<>();
        public final Set<String> pools = new HashSet<>();
        public boolean meta;

        public boolean isEmpty() {
            return nodes.isEmpty() && !meta;
        }
    }

    private EditOps() {
    }

    public static Changes apply(AtlasDefinition d, JsonArray ops) {
        if (ops.size() > MAX_OPS) {
            throw new AtlasException("skill_atlas.error.too_many", "ops", String.valueOf(MAX_OPS));
        }
        Changes c = new Changes();
        Set<String> deleted = new HashSet<>();
        for (JsonElement e : ops) {
            JsonObject o = Json.object(e, "op");
            String op = Json.string(o, "op", "", 32);
            switch (op) {
                case "node" -> {
                    SkillNode n = SkillNode.fromJson(o.get("node"));
                    SkillNode old = d.nodes.get(n.id);
                    boolean create = Json.bool(o, "create", false);
                    if (create && old != null) {
                        throw new AtlasException("skill_atlas.error.duplicate", n.id);
                    }
                    if (!create && old == null) {
                        throw new AtlasException("skill_atlas.error.missing_node", n.id);
                    }
                    if (old != null && n.maxLevel() < old.maxLevel()) {
                        c.shrunk.add(n.id);
                    }
                    d.nodes.put(n.id, n);
                    deleted.remove(n.id);
                    c.nodes.add(n.id);
                }
                case "move" -> {
                    for (JsonElement m : Json.array(o, "nodes")) {
                        JsonArray a = m.getAsJsonArray();
                        String id = a.get(0).getAsString();
                        SkillNode n = d.nodes.get(id);
                        if (n == null) {
                            continue;
                        }
                        n.x = clamp(a.get(1).getAsInt());
                        n.y = clamp(a.get(2).getAsInt());
                        c.nodes.add(id);
                    }
                }
                case "delete_node" -> {
                    for (JsonElement id : Json.array(o, "ids")) {
                        String s = id.getAsString();
                        if (d.nodes.remove(s) != null) {
                            deleted.add(s);
                            c.nodes.add(s);
                            c.shrunk.add(s);
                        }
                    }
                }
                case "category" -> {
                    Category cat = Category.fromJson(o.get("category"));
                    boolean create = Json.bool(o, "create", false);
                    if (create && d.categories.containsKey(cat.id)) {
                        throw new AtlasException("skill_atlas.error.duplicate", cat.id);
                    }
                    d.categories.put(cat.id, cat);
                    c.categories.add(cat.id);
                    c.meta = true;
                }
                case "delete_category" -> {
                    String id = Json.id(o, "id");
                    if (d.categories.remove(id) != null) {
                        List<String> inside = new ArrayList<>();
                        for (SkillNode n : d.nodes.values()) {
                            if (n.category.equals(id)) {
                                inside.add(n.id);
                            }
                        }
                        for (String s : inside) {
                            d.nodes.remove(s);
                            deleted.add(s);
                            c.nodes.add(s);
                            c.shrunk.add(s);
                        }
                        c.meta = true;
                    }
                }
                case "order_categories" -> {
                    Map<String, Category> ordered = new LinkedHashMap<>();
                    for (JsonElement id : Json.array(o, "ids")) {
                        Category cat = d.categories.get(id.getAsString());
                        if (cat != null) {
                            ordered.put(cat.id, cat);
                        }
                    }
                    for (Category cat : d.categories.values()) {
                        ordered.putIfAbsent(cat.id, cat);
                    }
                    d.categories.clear();
                    d.categories.putAll(ordered);
                    c.meta = true;
                }
                case "pool" -> {
                    PointPool p = PointPool.fromJson(o.get("pool"));
                    boolean create = Json.bool(o, "create", false);
                    if (create && d.pools.containsKey(p.id)) {
                        throw new AtlasException("skill_atlas.error.duplicate", p.id);
                    }
                    d.pools.put(p.id, p);
                    c.pools.add(p.id);
                    c.meta = true;
                }
                case "delete_pool" -> {
                    String id = Json.id(o, "id");
                    for (Category cat : d.categories.values()) {
                        if (cat.pool.equals(id)) {
                            throw new AtlasException("skill_atlas.error.pool_in_use", id);
                        }
                    }
                    if (d.pools.remove(id) != null) {
                        c.meta = true;
                    }
                }
                default -> throw new AtlasException("skill_atlas.error.bad_op", op);
            }
        }
        if (!deleted.isEmpty()) {
            for (SkillNode n : d.nodes.values()) {
                if (n.prerequisites.removeIf(p -> deleted.contains(p.node))) {
                    c.nodes.add(n.id);
                }
            }
        }
        return c;
    }

    public static JsonObject patch(AtlasDefinition d, Changes c, long revision) {
        JsonObject o = new JsonObject();
        o.addProperty("revision", revision);
        JsonObject n = new JsonObject();
        for (String id : c.nodes) {
            n.add(id, AtlasDefinition.nodeOrNull(d, id));
        }
        o.add("nodes", n);
        o.addProperty("meta", c.meta);
        if (c.meta) {
            o.add("pools", d.poolsJson());
            o.add("categories", d.categoriesJson());
        }
        return o;
    }

    public static void applyPatch(AtlasDefinition d, JsonObject patch) {
        if (Json.bool(patch, "meta", false)) {
            d.readMeta(patch);
        }
        JsonObject n = patch.getAsJsonObject("nodes");
        if (n != null) {
            for (Map.Entry<String, JsonElement> e : n.entrySet()) {
                if (e.getValue() == null || e.getValue().isJsonNull()) {
                    d.nodes.remove(e.getKey());
                } else {
                    SkillNode node = SkillNode.fromJson(e.getValue());
                    d.nodes.put(node.id, node);
                }
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(-SkillNode.MAX_COORD, Math.min(SkillNode.MAX_COORD, v));
    }

    public static JsonObject request(int id, JsonArray ops) {
        JsonObject o = new JsonObject();
        o.addProperty("request", id);
        o.add("ops", ops);
        return o;
    }

    public static JsonObject nodeOp(SkillNode n, boolean create) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "node");
        o.addProperty("create", create);
        o.add("node", n.toJson());
        return o;
    }

    public static JsonObject moveOp(Collection<SkillNode> nodes) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "move");
        JsonArray a = new JsonArray();
        for (SkillNode n : nodes) {
            JsonArray m = new JsonArray();
            m.add(n.id);
            m.add(n.x);
            m.add(n.y);
            a.add(m);
        }
        o.add("nodes", a);
        return o;
    }

    public static JsonObject deleteNodesOp(Collection<String> ids) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "delete_node");
        JsonArray a = new JsonArray();
        for (String id : ids) {
            a.add(id);
        }
        o.add("ids", a);
        return o;
    }

    public static JsonObject categoryOp(Category c, boolean create) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "category");
        o.addProperty("create", create);
        o.add("category", c.toJson());
        return o;
    }

    public static JsonObject orderCategoriesOp(Collection<String> ids) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "order_categories");
        JsonArray a = new JsonArray();
        for (String id : ids) {
            a.add(id);
        }
        o.add("ids", a);
        return o;
    }

    public static JsonObject poolOp(PointPool p, boolean create) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "pool");
        o.addProperty("create", create);
        o.add("pool", p.toJson());
        return o;
    }

    public static JsonObject deleteOp(String op, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.addProperty("id", id);
        return o;
    }
}
