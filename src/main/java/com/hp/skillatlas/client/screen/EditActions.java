package com.hp.skillatlas.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasTexts;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.Icon;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.data.Prerequisite;
import com.hp.skillatlas.data.SkillLevel;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EditActions {
    private static final String MARKER = "skill_atlas";
    private static final int MAX_CLIPBOARD = 8 * 1024 * 1024;
    private static AtlasTexts clipboardTexts = new AtlasTexts();

    private EditActions() {
    }

    public static void send(MainLayer main, JsonArray ops, Runnable onOk) {
        if (ClientAtlas.locked) {
            main.error(Component.translatable("skill_atlas.ui.locked"));
            return;
        }
        ClientAtlas.edit(ops, r -> {
            if (r.ok()) {
                if (onOk != null) {
                    onOk.run();
                }
            } else {
                main.error(Names.error(r.key(), r.args()));
            }
        });
    }

    public static String freshId(String prefix, Set<String> taken) {
        for (int i = 1; ; i++) {
            String suffix = "_" + i;
            String root = prefix;
            if (root.length() + suffix.length() > 64) {
                root = root.substring(0, 64 - suffix.length());
            }
            String id = root + suffix;
            if (!taken.contains(id)) {
                return id;
            }
        }
    }

    public static String uniqueId(String base, Set<String> taken) {
        if (!taken.contains(base)) {
            return base;
        }
        String root = base.replaceAll("_\\d+$", "");
        if (root.isEmpty()) {
            root = "skill";
        }
        return freshId(root, taken);
    }

    public static void create(MainLayer main, int wx, int wy) {
        Category cat = ClientAtlas.definition.categories.get(ClientAtlas.category);
        if (cat == null) {
            main.error(Component.translatable("skill_atlas.ui.need_category"));
            return;
        }
        SkillNode n = new SkillNode();
        n.id = freshId("skill", ClientAtlas.definition.nodes.keySet());
        n.category = cat.id;
        n.icon = Icon.item("minecraft:book");
        n.x = wx;
        n.y = wy;
        n.levels.add(new SkillLevel(1));
        NodeEditorDialog.create(main, n);
    }

    public static void move(MainLayer main, List<SkillNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        JsonArray ops = new JsonArray();
        ops.add(EditOps.moveOp(nodes));
        ClientAtlas.edit(ops, r -> {
            if (!r.ok()) {
                main.error(Names.error(r.key(), r.args()));
                ClientAtlas.requestResync();
            }
        });
    }

    public static Component linkProblem(SkillNode parent, SkillNode child) {
        if (parent.id.equals(child.id)) {
            return Component.translatable("skill_atlas.error.self_prerequisite", Names.node(child));
        }
        if (child.prerequisite(parent.id) != null) {
            return Component.translatable("skill_atlas.ui.link.exists");
        }
        if (ClientAtlas.definition.dependsOn(parent.id, child.id)) {
            return Component.translatable("skill_atlas.error.cycle", Names.node(child));
        }
        if (child.prerequisites.size() >= SkillNode.MAX_PREREQUISITES) {
            return Names.error("skill_atlas.error.too_many", List.of("prerequisites", String.valueOf(SkillNode.MAX_PREREQUISITES)));
        }
        return null;
    }

    public static void link(MainLayer main, List<SkillNode> parents, SkillNode target) {
        link(main, parents, List.of(target));
    }

    public static void link(MainLayer main, List<SkillNode> parents, List<SkillNode> children) {
        JsonArray ops = new JsonArray();
        int added = 0;
        Component problem = null;
        for (SkillNode child : children) {
            SkillNode copy = child.copy();
            int before = added;
            for (SkillNode parent : parents) {
                Component p = linkProblem(parent, copy);
                if (p != null) {
                    problem = p;
                    continue;
                }
                copy.prerequisites.add(new Prerequisite(parent.id, true));
                added++;
            }
            if (added > before) {
                ops.add(EditOps.nodeOp(copy, false));
            }
        }
        if (added == 0) {
            if (problem != null) {
                main.error(problem);
            }
            return;
        }
        int count = added;
        send(main, ops, () -> main.status(Component.translatable("skill_atlas.ui.linked", count), Theme.TEAL));
    }

    public static boolean linked(List<SkillNode> parents, List<SkillNode> children) {
        for (SkillNode child : children) {
            for (SkillNode parent : parents) {
                if (child.prerequisite(parent.id) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void unlink(MainLayer main, List<SkillNode> parents, List<SkillNode> children) {
        Set<String> ids = new HashSet<>();
        for (SkillNode parent : parents) {
            ids.add(parent.id);
        }
        JsonArray ops = new JsonArray();
        for (SkillNode child : children) {
            SkillNode copy = child.copy();
            if (copy.prerequisites.removeIf(p -> ids.contains(p.node))) {
                ops.add(EditOps.nodeOp(copy, false));
            }
        }
        if (!ops.isEmpty()) {
            send(main, ops, () -> main.status(Component.translatable("skill_atlas.ui.unlinked"), Theme.MUTED));
        }
    }

    public static void copyId(MainLayer main, SkillNode n) {
        Minecraft.getInstance().keyboardHandler.setClipboard(n.id);
        main.status(Component.translatable("skill_atlas.ui.copied_id", n.id), Theme.TEAL);
    }

    public static void undo(MainLayer main) {
        if (ClientAtlas.locked) {
            main.error(Component.translatable("skill_atlas.ui.locked"));
            return;
        }
        boolean started = ClientAtlas.undo(r -> {
            if (r.ok()) {
                main.status(Component.translatable("skill_atlas.ui.undone"), Theme.TEAL);
            } else {
                main.error(Names.error(r.key(), r.args()));
            }
        });
        if (!started) {
            main.status(Component.translatable("skill_atlas.ui.nothing_to_undo"), Theme.MUTED);
        }
    }

    public static void setLine(MainLayer main, SkillNode child, String parentId, boolean line) {
        SkillNode copy = child.copy();
        Prerequisite p = copy.prerequisite(parentId);
        if (p == null) {
            return;
        }
        p.line = line;
        JsonArray ops = new JsonArray();
        ops.add(EditOps.nodeOp(copy, false));
        send(main, ops, null);
    }

    public static void unlink(MainLayer main, SkillNode child, String parentId) {
        SkillNode copy = child.copy();
        if (!copy.prerequisites.removeIf(p -> p.node.equals(parentId))) {
            return;
        }
        JsonArray ops = new JsonArray();
        ops.add(EditOps.nodeOp(copy, false));
        send(main, ops, () -> main.status(Component.translatable("skill_atlas.ui.unlinked"), Theme.MUTED));
    }

    public static void setMajor(MainLayer main, List<SkillNode> nodes, boolean major) {
        JsonArray ops = new JsonArray();
        for (SkillNode n : nodes) {
            if (n.major != major) {
                SkillNode c = n.copy();
                c.major = major;
                ops.add(EditOps.nodeOp(c, false));
            }
        }
        if (!ops.isEmpty()) {
            send(main, ops, null);
        }
    }

    public static void setHidden(MainLayer main, List<SkillNode> nodes, boolean hidden) {
        JsonArray ops = new JsonArray();
        for (SkillNode n : nodes) {
            if (n.hidden != hidden) {
                SkillNode c = n.copy();
                c.hidden = hidden;
                ops.add(EditOps.nodeOp(c, false));
            }
        }
        if (!ops.isEmpty()) {
            send(main, ops, null);
        }
    }

    public static void delete(MainLayer main, List<SkillNode> nodes) {
        delete(main, nodes, true);
    }

    public static void delete(MainLayer main, List<SkillNode> nodes, boolean confirm) {
        if (nodes.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (SkillNode n : nodes) {
            ids.add(n.id);
        }
        if (!confirm) {
            JsonArray ops = new JsonArray();
            ops.add(EditOps.deleteNodesOp(ids));
            send(main, ops, () -> {
                ids.forEach(ClientAtlas.SELECTION::remove);
                main.status(Component.translatable("skill_atlas.ui.deleted", ids.size()), Theme.MUTED);
            });
            return;
        }
        Component message = Component.translatable("skill_atlas.ui.confirm_delete", ids.size());
        if (ids.size() == 1) {
            message = Component.translatable("skill_atlas.ui.confirm_delete.one", Names.node(nodes.get(0)));
        }
        main.stack.push(new ConfirmDialog(Component.translatable("skill_atlas.ui.delete_nodes"), List.of(message, Component.translatable("skill_atlas.ui.confirm_delete.refund")), Component.translatable("skill_atlas.ui.delete"), () -> {
            JsonArray ops = new JsonArray();
            ops.add(EditOps.deleteNodesOp(ids));
            send(main, ops, () -> {
                ids.forEach(ClientAtlas.SELECTION::remove);
                main.status(Component.translatable("skill_atlas.ui.deleted", ids.size()), Theme.MUTED);
            });
        }));
    }

    public static void copy(MainLayer main, List<SkillNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        List<SkillNode> copies = new ArrayList<>();
        JsonArray arr = new JsonArray();
        AtlasDefinition picked = new AtlasDefinition();
        for (SkillNode n : nodes) {
            copies.add(n.copy());
            arr.add(n.toJson());
            picked.nodes.put(n.id, n);
        }
        Set<String> keys = new HashSet<>();
        AtlasTexts.collect(picked, keys);
        AtlasTexts texts = new AtlasTexts();
        for (String key : keys) {
            ClientAtlas.definition.texts.entries(key).forEach((lang, text) -> texts.put(lang, key, text));
        }
        ClientAtlas.clipboard = copies;
        clipboardTexts = texts;
        JsonObject o = new JsonObject();
        o.addProperty(MARKER, "nodes");
        o.add("nodes", arr);
        o.add("texts", texts.toJson());
        Minecraft.getInstance().keyboardHandler.setClipboard(Json.COMPACT.toJson(o));
        main.status(Component.translatable("skill_atlas.ui.copied", copies.size()), Theme.TEAL);
    }

    public static void cut(MainLayer main, List<SkillNode> nodes) {
        copy(main, nodes);
        delete(main, nodes);
    }

    private static String systemClipboard() {
        String s = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (s == null || s.length() > MAX_CLIPBOARD || !s.contains("\"" + MARKER + "\"")) {
            return null;
        }
        return s;
    }

    public static boolean hasClipboard() {
        return !ClientAtlas.clipboard.isEmpty() || systemClipboard() != null;
    }

    private static List<SkillNode> readClipboard() {
        String s = systemClipboard();
        if (s != null) {
            try {
                JsonObject o = JsonParser.parseString(s).getAsJsonObject();
                if ("nodes".equals(o.get(MARKER).getAsString())) {
                    List<SkillNode> out = new ArrayList<>();
                    for (JsonElement e : o.getAsJsonArray("nodes")) {
                        out.add(SkillNode.fromJson(e));
                    }
                    if (!out.isEmpty()) {
                        clipboardTexts = AtlasTexts.fromJson(o.getAsJsonObject("texts"));
                        return out;
                    }
                }
            } catch (RuntimeException e) {
                SkillAtlas.LOGGER.debug("Ignoring clipboard content that is not a skill atlas node list: {}", e.toString());
            }
        }
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode n : ClientAtlas.clipboard) {
            out.add(n.copy());
        }
        return out;
    }

    private static String pastedText(String raw, String lang) {
        if (!AtlasTexts.isKey(raw) || ClientAtlas.definition.texts.has(raw)) {
            return raw;
        }
        String text = clipboardTexts.resolve(raw, lang);
        if (text == null) {
            return raw;
        }
        return text;
    }

    public static void paste(MainLayer main, int wx, int wy) {
        paste(main, wx, wy, true);
    }

    public static void paste(MainLayer main, int wx, int wy, boolean external) {
        AtlasDefinition d = ClientAtlas.definition;
        if (!d.categories.containsKey(ClientAtlas.category)) {
            main.error(Component.translatable("skill_atlas.ui.need_category"));
            return;
        }
        List<SkillNode> src = readClipboard();
        if (src.isEmpty()) {
            main.error(Component.translatable("skill_atlas.ui.clipboard_empty"));
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (SkillNode n : src) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x);
            maxY = Math.max(maxY, n.y);
        }
        int offX = wx - CanvasView.snap((minX + (double) maxX) / 2.0D);
        int offY = wy - CanvasView.snap((minY + (double) maxY) / 2.0D);
        Set<String> taken = new HashSet<>(d.nodes.keySet());
        Map<String, String> ids = new HashMap<>();
        for (SkillNode n : src) {
            String id = uniqueId(n.id, taken);
            taken.add(id);
            ids.put(n.id, id);
        }
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        JsonArray ops = new JsonArray();
        List<String> created = new ArrayList<>();
        for (SkillNode n : src) {
            SkillNode c = n.copy();
            c.id = ids.get(n.id);
            c.category = ClientAtlas.category;
            c.x = Math.max(-SkillNode.MAX_COORD, Math.min(SkillNode.MAX_COORD, n.x + offX));
            c.y = Math.max(-SkillNode.MAX_COORD, Math.min(SkillNode.MAX_COORD, n.y + offY));
            c.name = pastedText(c.name, lang);
            c.aliases = pastedText(c.aliases, lang);
            c.tooltip = pastedText(c.tooltip, lang);
            c.prerequisites.clear();
            for (Prerequisite p : n.prerequisites) {
                String mapped = ids.get(p.node);
                if (mapped != null) {
                    c.prerequisites.add(new Prerequisite(mapped, p.line));
                } else if (external && d.nodes.containsKey(p.node)) {
                    c.prerequisites.add(p.copy());
                }
            }
            ops.add(EditOps.nodeOp(c, true));
            created.add(c.id);
        }
        send(main, ops, () -> {
            ClientAtlas.SELECTION.clear();
            ClientAtlas.SELECTION.addAll(created);
            main.status(Component.translatable("skill_atlas.ui.pasted", created.size()), Theme.TEAL);
        });
    }
}
