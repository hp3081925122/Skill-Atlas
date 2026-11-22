package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import com.hp.skillatlas.data.*;
import java.util.Map;

public final class CategoryBuilder {
    private final DefinitionsEvent event;
    private final String id;
    CategoryBuilder(DefinitionsEvent event, String id) { this.event = event; this.id = id; }
    private Category value() { Category c = event.draft().categories.get(id); if (c == null) throw new IllegalStateException("Category removed"); return c; }
    public CategoryBuilder name(String name) { value().name = name; return this; }
    public CategoryBuilder icon(String item) { value().icon = Icon.item(Json.resource(item, "icon")); return this; }
    public CategoryBuilder pool(String pool) { value().pool = pool.isEmpty() ? "" : Values.id(pool); return this; }
    public CategoryBuilder background(String texture, boolean tile) { value().background = texture.isEmpty() ? "" : Json.resource(texture, "background"); value().tile = tile; return this; }
    public CategoryBuilder json(Map<?, ?> patch) {
        JsonObject json = value().toJson(); Values.json(patch).entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        if (!id.equals(json.get("id").getAsString())) throw new IllegalArgumentException("Category IDs cannot be renamed");
        event.draft().categories.put(id, Category.fromJson(json)); return this;
    }
}
