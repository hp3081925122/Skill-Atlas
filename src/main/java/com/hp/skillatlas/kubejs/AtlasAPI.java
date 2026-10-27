package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.data.*;
import com.hp.skillatlas.server.AtlasServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class AtlasAPI {
    private AtlasAPI() {}
    public static PlayerAPI player(ServerPlayer player) { return new PlayerAPI(Objects.requireNonNull(player)); }
    public static long getRevision() { return Hooks.revision(); }
    public static boolean hasNode(String id) { return AtlasServer.definition().nodes.containsKey(id); }
    public static Object getNode(String id) {
        var definition = AtlasServer.definition(); SkillNode n = definition.nodes.get(id);
        if (n == null) return null;
        var json = n.toJson(); if (ExtraData.has(definition, id)) json.add("kubejs", ExtraData.node(definition, id).deepCopy());
        return Values.snapshot(json);
    }
    public static Object getCategory(String id) { Category c = AtlasServer.definition().categories.get(id); return c == null ? null : Values.snapshot(c.toJson()); }
    public static Object getPool(String id) {
        var definition = AtlasServer.definition(); PointPool p = definition.pools.get(id); if (p == null) return null;
        var json = p.toJson(); var custom = Acquisitions.binding(definition, id); if (custom != null) json.add("kubejs", custom.deepCopy());
        return Values.snapshot(json);
    }
    public static List<String> getNodeIds() { return List.copyOf(AtlasServer.definition().nodes.keySet()); }
    public static List<String> getNodeIds(String category) { return AtlasServer.definition().nodes.values().stream().filter(n -> n.category.equals(category)).map(n -> n.id).toList(); }
    public static List<String> getCategoryIds() { return List.copyOf(AtlasServer.definition().categories.keySet()); }
    public static List<String> getPoolIds() { return List.copyOf(AtlasServer.definition().pools.keySet()); }
    public static Object exportDefinition() { return Values.snapshot(AtlasServer.definition().toJson()); }
    public static Outcome validateDefinition(Map<?, ?> json) {
        try { var input = Values.json(json); var definition = AtlasDefinition.fromJson(input); ExtraData.read(input, definition); ExtraData.validate(definition, TypeRegistry.current()); return Outcome.ok(); }
        catch (RuntimeException e) { Outcome r = Outcome.fail("invalid_definition"); r.detail = e.getMessage(); return r; }
    }
}
