package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class AtlasTexts {
    public static final String PREFIX = "skill_atlas.atlas.";
    public static final String DEFAULT_LANG = "en_us";
    public static final int MAX_KEY = 160;
    public static final int MAX_TEXT = 4000;
    public static final int MAX_LANGS = 64;

    public final Map<String, Map<String, String>> langs = new TreeMap<>();

    public AtlasTexts copy() {
        AtlasTexts t = new AtlasTexts();
        langs.forEach((k, v) -> t.langs.put(k, new TreeMap<>(v)));
        return t;
    }

    public static String key(String kind, String id, String field) {
        return PREFIX + kind + "." + id + "." + field;
    }

    public static boolean isKey(String s) {
        return s != null && s.startsWith(PREFIX);
    }

    public static boolean validLang(String lang) {
        return lang != null && lang.matches("[a-z0-9_]{1,16}");
    }

    public static String lang(String lang) {
        if (lang == null) {
            return DEFAULT_LANG;
        }
        String l = lang.toLowerCase(Locale.ROOT);
        if (validLang(l)) {
            return l;
        }
        return DEFAULT_LANG;
    }

    public boolean has(String key) {
        for (Map<String, String> m : langs.values()) {
            if (m.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public void put(String lang, String key, String text) {
        langs.computeIfAbsent(lang, k -> new TreeMap<>()).put(key, text);
    }

    public Map<String, String> entries(String key) {
        Map<String, String> out = new LinkedHashMap<>();
        langs.forEach((lang, m) -> {
            String t = m.get(key);
            if (t != null) {
                out.put(lang, t);
            }
        });
        return out;
    }

    public boolean remove(String key) {
        boolean removed = false;
        for (Map<String, String> m : langs.values()) {
            if (m.remove(key) != null) {
                removed = true;
            }
        }
        return removed;
    }

    public void set(String key, Map<String, String> entries) {
        remove(key);
        entries.forEach((lang, text) -> put(lang, key, text));
    }

    public Set<String> keys() {
        Set<String> out = new TreeSet<>();
        for (Map<String, String> m : langs.values()) {
            out.addAll(m.keySet());
        }
        return out;
    }

    public String resolve(String key, String lang) {
        Map<String, String> m = langs.get(lang);
        if (m != null && m.containsKey(key)) {
            return m.get(key);
        }
        for (String fallback : new String[]{DEFAULT_LANG, "zh_cn"}) {
            m = langs.get(fallback);
            if (m != null && m.containsKey(key)) {
                return m.get(key);
            }
        }
        for (Map<String, String> any : langs.values()) {
            String t = any.get(key);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    public String display(String raw, String lang) {
        if (isKey(raw)) {
            String t = resolve(raw, lang);
            if (t != null) {
                return t;
            }
        }
        return raw;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        langs.forEach((lang, m) -> o.add(lang, langJson(m)));
        return o;
    }

    public static JsonObject langJson(Map<String, String> m) {
        JsonObject o = new JsonObject();
        m.forEach(o::addProperty);
        return o;
    }

    public void readLang(String lang, JsonObject o) {
        if (!validLang(lang) || (!langs.containsKey(lang) && langs.size() >= MAX_LANGS)) {
            return;
        }
        Map<String, String> m = langs.computeIfAbsent(lang, k -> new TreeMap<>());
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String key = e.getKey();
            if (!isKey(key) || key.length() > MAX_KEY || !e.getValue().isJsonPrimitive()) {
                continue;
            }
            String text = e.getValue().getAsString();
            if (text.length() > MAX_TEXT) {
                text = text.substring(0, MAX_TEXT);
            }
            m.put(key, text);
        }
    }

    public static AtlasTexts fromJson(JsonObject o) {
        AtlasTexts t = new AtlasTexts();
        if (o == null) {
            return t;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getValue().isJsonObject()) {
                t.readLang(e.getKey(), e.getValue().getAsJsonObject());
            }
        }
        return t;
    }

    public static void collect(AtlasDefinition d, Collection<String> out) {
        for (SkillNode n : d.nodes.values()) {
            add(out, n.name);
            add(out, n.aliases);
            add(out, n.tooltip);
        }
        for (Category c : d.categories.values()) {
            add(out, c.name);
        }
        for (PointPool p : d.pools.values()) {
            add(out, p.name);
            add(out, p.description);
        }
    }

    private static void add(Collection<String> out, String raw) {
        if (isKey(raw)) {
            out.add(raw);
        }
    }
}
