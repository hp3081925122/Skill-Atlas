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
        public final Set<String> texts = new LinkedHashSet<>();
        public boolean meta;

        public boolean isEmpty() {
            return nodes.isEmpty() && !meta && texts.isEmpty();
        }
    }

    private EditOps() {
    }

    public static Changes apply(AtlasDefinition d, JsonArray ops) {
        return apply(d, ops, null);
    }

    public static Changes apply(AtlasDefinition d, JsonArray ops, String lang) {
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
                    if (lang != null) {
                        SkillNode prev = old;
                        if (prev == null) {
                            prev = new SkillNode();
                        }
                        n.name = localize(d, c, lang, AtlasTexts.key("node", n.id, "name"), n.name, prev.name);
                        n.aliases = localize(d, c, lang, AtlasTexts.key("node", n.id, "aliases"), n.aliases, prev.aliases);
                        n.tooltip = localize(d, c, lang, AtlasTexts.key("node", n.id, "tooltip"), n.tooltip, prev.tooltip);
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
                    if (lang != null) {
                        String prev = "";
                        if (d.categories.containsKey(cat.id)) {
                            prev = d.categories.get(cat.id).name;
                        }
                        cat.name = localize(d, c, lang, AtlasTexts.key("category", cat.id, "name"), cat.name, prev);
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
                    if (lang != null) {
                        PointPool prev = d.pools.get(p.id);
                        if (prev == null) {
                            prev = new PointPool();
                        }
                        p.name = localize(d, c, lang, AtlasTexts.key("pool", p.id, "name"), p.name, prev.name);
                        p.description = localize(d, c, lang, AtlasTexts.key("pool", p.id, "description"), p.description, prev.description);
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
                case "texts" -> {
                    for (Map.Entry<String, JsonElement> t : Json.object(o.get("texts"), "texts").entrySet()) {
                        String key = t.getKey();
                        if (!AtlasTexts.isKey(key) || key.length() > AtlasTexts.MAX_KEY) {
                            throw new AtlasException("skill_atlas.error.bad_op", op);
                        }
                        AtlasTexts parsed = new AtlasTexts();
                        for (Map.Entry<String, JsonElement> l : Json.object(t.getValue(), key).entrySet()) {
                            if (d.texts.langs.containsKey(l.getKey()) || d.texts.langs.size() < AtlasTexts.MAX_LANGS) {
                                JsonObject one = new JsonObject();
                                one.add(key, l.getValue());
                                parsed.readLang(l.getKey(), one);
                            }
                        }
                        d.texts.set(key, parsed.entries(key));
                        c.texts.add(key);
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
        prune(d, c);
        return c;
    }

    private static String localize(AtlasDefinition d, Changes c, String lang, String own, String value, String previous) {
        if (value.isEmpty()) {
            return "";
        }
        if (AtlasTexts.isKey(value)) {
            if (value.equals(own) || !d.texts.has(value)) {
                return value;
            }
            d.texts.set(own, d.texts.entries(value));
            c.texts.add(own);
            return own;
        }
        if (value.matches("[a-z0-9_-]+([.][a-z0-9_-]+)+")) {
            return value;
        }
        if (AtlasTexts.isKey(previous) && value.equals(d.texts.resolve(previous, lang))) {
            return localize(d, c, lang, own, previous, "");
        }
        d.texts.put(lang, own, value);
        c.texts.add(own);
        return own;
    }

    public static void prune(AtlasDefinition d, Changes c) {
        Set<String> used = new HashSet<>();
        AtlasTexts.collect(d, used);
        for (String key : d.texts.keys()) {
            if (!used.contains(key)) {
                d.texts.remove(key);
                c.texts.add(key);
            }
        }
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
        if (!c.texts.isEmpty()) {
            JsonObject t = new JsonObject();
            for (String key : c.texts) {
                t.add(key, AtlasTexts.langJson(d.texts.entries(key)));
            }
            o.add("texts", t);
        }
        return o;
    }

    public static void applyPatch(AtlasDefinition d, JsonObject patch) {
        JsonObject t = patch.getAsJsonObject("texts");
        if (t != null) {
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                d.texts.remove(e.getKey());
                if (e.getValue() != null && e.getValue().isJsonObject()) {
                    for (Map.Entry<String, JsonElement> l : e.getValue().getAsJsonObject().entrySet()) {
                        if (AtlasTexts.validLang(l.getKey()) && AtlasTexts.isKey(e.getKey())) {
                            d.texts.put(l.getKey(), e.getKey(), l.getValue().getAsString());
                        }
                    }
                }
            }
        }
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

    public static JsonObject textsOp(AtlasTexts source, Collection<String> keys) {
        JsonObject t = new JsonObject();
        for (String key : keys) {
            t.add(key, AtlasTexts.langJson(source.entries(key)));
        }
        JsonObject o = new JsonObject();
        o.addProperty("op", "texts");
        o.add("texts", t);
        return o;
    }

    public static JsonObject deleteOp(String op, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.addProperty("id", id);
        return o;
    }
}
