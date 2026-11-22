package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import com.hp.skillatlas.data.*;
import java.util.*;

public final class PoolBuilder {
    private final DefinitionsEvent event;
    private final String id;
    PoolBuilder(DefinitionsEvent event, String id) { this.event = event; this.id = id; }
    private PointPool value() { PointPool p = event.draft().pools.get(id); if (p == null) throw new IllegalStateException("Pool removed"); return p; }
    public PoolBuilder name(String name) { value().name = name; return this; }
    public PoolBuilder initial(Number initial) { value().initial = (int)Values.integer(initial, 0, 1000000000); return this; }
    public PoolBuilder color(Number color) { value().color = (int)Values.integer(color, 0, 0xffffff); return this; }
    public PoolBuilder description(String text) { value().description = text; return this; }
    public PoolBuilder acquisition(String method, Number count, Number points, Number limit) {
        JsonObject json = value().toJson(); json.addProperty("method", method); json.addProperty("count", Values.integer(count, 1, 1000000000)); json.addProperty("points", Values.integer(points, 1, 1000000)); json.addProperty("limit", Values.integer(limit, 0, 1000000000));
        event.draft().pools.put(id, PointPool.fromJson(json)); Acquisitions.remove(event.draft(), id); return this;
    }
    public PoolBuilder customAcquisition(String type, Map<?, ?> data, Number count, Number points, Number limit) {
        JsonObject json = value().toJson(); json.addProperty("method", "playtime"); json.add("targets", new com.google.gson.JsonArray());
        json.addProperty("count", Values.integer(count, 1, 1000000000)); json.addProperty("points", Values.integer(points, 1, 1000000)); json.addProperty("limit", Values.integer(limit, 0, 1000000000));
        PointPool configured = PointPool.fromJson(json); Acquisitions.configure(event.draft(), id, type, data); event.draft().pools.put(id, configured); return this;
    }
    public PoolBuilder targets(List<String> targets) { value().targets.clear(); value().targets.addAll(targets); return this; }
    public PoolBuilder json(Map<?, ?> patch) {
        JsonObject json = value().toJson(); Values.json(patch).entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        if (!id.equals(json.get("id").getAsString())) throw new IllegalArgumentException("Pool IDs cannot be renamed");
        event.draft().pools.put(id, PointPool.fromJson(json));
        if (patch.containsKey("method")) Acquisitions.remove(event.draft(), id);
        return this;
    }
}
