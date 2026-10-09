package com.hp.skillatlas.core;

import com.hp.skillatlas.model.Definition;
import java.util.*;

public final class NodeClipboard {
    private final Map<String, Definition.Node> nodes = new LinkedHashMap<>();
    private final List<Definition.Line> lines = new ArrayList<>();
    public void copy(Definition d, Set<String> selected) {
        nodes.clear(); lines.clear();
        selected.forEach(id -> { if (d.nodes.containsKey(id)) nodes.put(id, d.nodes.get(id).copy()); });
        d.lines.stream().filter(l -> selected.contains(l.from) && selected.contains(l.to)).forEach(l -> lines.add(Definition.JSON.fromJson(Definition.JSON.toJson(l), Definition.Line.class)));
    }
    public Set<String> paste(Definition d, String category, double x, double y, boolean external) {
        Set<String> result = new LinkedHashSet<>(); if (nodes.isEmpty()) return result;
        double minX = nodes.values().stream().mapToDouble(n -> n.x).min().orElse(0), minY = nodes.values().stream().mapToDouble(n -> n.y).min().orElse(0);
        Map<String, String> ids = new HashMap<>(); nodes.keySet().forEach(k -> ids.put(k, "node_" + UUID.randomUUID().toString().replace("-", "")));
        nodes.forEach((id, source) -> {
            var n = source.copy(); n.category = category; n.x += x - minX; n.y += y - minY;
            n.prerequisites.removeIf(r -> !ids.containsKey(r.node) && (!external || !d.nodes.containsKey(r.node)));
            n.prerequisites.forEach(r -> r.node = ids.getOrDefault(r.node, r.node));
            d.nodes.put(ids.get(id), n); result.add(ids.get(id));
        });
        lines.forEach(l -> { var copy = new Definition.Line(ids.get(l.from), ids.get(l.to)); copy.visible = l.visible; d.lines.add(copy); });
        return result;
    }
}
