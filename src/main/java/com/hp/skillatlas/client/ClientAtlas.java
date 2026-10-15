package com.hp.skillatlas.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hp.skillatlas.ItemCosts;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasTexts;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.ExperienceMath;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.PointPool;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.network.LearnPacket;
import com.hp.skillatlas.network.Payload;
import com.hp.skillatlas.network.ResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ClientAtlas {
    public static final class View {
        public float panX;
        public float panY;
        public float zoom = 1.0F;
        public boolean initialized;
    }

    private static final class Undo {
        final Map<String, SkillNode> nodes = new LinkedHashMap<>();
        final Map<String, Category> categories = new LinkedHashMap<>();
        final Map<String, PointPool> pools = new LinkedHashMap<>();
        final AtlasTexts texts = new AtlasTexts();
        final Set<String> keys = new LinkedHashSet<>();
        List<String> order;

        boolean isEmpty() {
            return nodes.isEmpty() && categories.isEmpty() && pools.isEmpty() && order == null && keys.isEmpty();
        }
    }

    public static final int MAX_UNDO = 50;

    public static AtlasDefinition definition = new AtlasDefinition();
    public static PlayerProgress progress = new PlayerProgress();
    public static long revision = Long.MIN_VALUE;
    public static boolean ready;
    public static boolean locked;
    public static boolean canEdit;
    public static boolean editMode;
    public static int version;
    public static int progressVersion;
    public static String category = "";
    public static final Map<String, View> VIEWS = new HashMap<>();
    public static final Set<String> SELECTION = new LinkedHashSet<>();
    public static List<SkillNode> clipboard = new ArrayList<>();
    private static final Map<Integer, Consumer<ResultPacket>> CALLBACKS = new HashMap<>();
    private static int nextRequest = 1;
    private static boolean resyncRequested;
    private static final Deque<Undo> UNDO = new ArrayDeque<>();
    private static AtlasDefinition baseline = new AtlasDefinition();
    private static AtlasDefinition patchBefore;
    private static JsonObject lastPatch;

    private ClientAtlas() {
    }

    public static boolean hasItems(List<ItemCost> costs) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return costs.isEmpty();
        }
        return ItemCosts.has(player.getInventory(), costs);
    }

    public static int[] ownedItems(List<ItemCost> costs) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new int[costs.size()];
        }
        return ItemCosts.owned(player.getInventory(), costs);
    }

    public static int inventoryStamp() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        return player.getInventory().getTimesChanged();
    }

    public static long experiencePoints() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : ExperienceMath.available(player.experienceLevel, player.experienceProgress, player.getXpNeededForNextLevel());
    }

    public static void reset() {
        definition = new AtlasDefinition();
        progress = new PlayerProgress();
        revision = Long.MIN_VALUE;
        ready = false;
        locked = false;
        canEdit = false;
        editMode = false;
        category = "";
        VIEWS.clear();
        SELECTION.clear();
        CALLBACKS.clear();
        resyncRequested = false;
        UNDO.clear();
        baseline = new AtlasDefinition();
        patchBefore = null;
        lastPatch = null;
        version++;
        progressVersion++;
    }

    public static View view(String cat) {
        return VIEWS.computeIfAbsent(cat, k -> new View());
    }

    public static void onFull(JsonObject o) {
        try {
            AtlasDefinition d = AtlasDefinition.fromJson(o.getAsJsonObject("definition"));
            definition = d;
            revision = o.get("revision").getAsLong();
            locked = o.get("locked").getAsBoolean();
            ready = true;
            resyncRequested = false;
            lastPatch = null;
            patchBefore = null;
            changed();
            SkillAtlas.LOGGER.debug("Received full skill atlas revision {} with {} nodes", revision, definition.nodes.size());
        } catch (RuntimeException e) {
            SkillAtlas.LOGGER.error("Failed to read skill atlas definition from server: {}", e.toString());
        }
    }

    public static void onPatch(JsonObject o) {
        long rev = o.get("revision").getAsLong();
        if (!ready || rev != revision + 1) {
            SkillAtlas.LOGGER.debug("Skill atlas patch revision {} does not follow {}, requesting resync", rev, revision);
            requestResync();
            return;
        }
        try {
            int previousNodes = definition.nodes.size();
            AtlasDefinition next = definition.copy();
            EditOps.applyPatch(next, o);
            patchBefore = baseline;
            lastPatch = o;
            definition = next;
            revision = rev;
            changed();
            SkillAtlas.LOGGER.debug("Applied skill atlas patch revision {}: node count {} -> {}", revision, previousNodes, definition.nodes.size());
        } catch (RuntimeException e) {
            SkillAtlas.LOGGER.warn("Failed to apply skill atlas patch: {}", e.toString());
            requestResync();
        }
    }

    private static void changed() {
        if (!definition.categories.containsKey(category)) {
            category = "";
            if (!definition.categories.isEmpty()) {
                category = definition.categories.keySet().iterator().next();
            }
        }
        SELECTION.removeIf(id -> !definition.nodes.containsKey(id));
        baseline = definition.copy();
        if (locked || !canEdit) {
            editMode = false;
        }
        version++;
    }

    public static void onProgress(PlayerProgress p, boolean edit) {
        progress = p;
        canEdit = edit;
        if (!edit || locked) {
            editMode = false;
        }
        progressVersion++;
    }

    public static void requestResync() {
        if (resyncRequested) {
            return;
        }
        resyncRequested = true;
        sendSmall(Payload.RESYNC);
    }

    public static void requestProgress() {
        sendSmall(Payload.PROGRESS);
    }

    private static void sendSmall(byte kind) {
        for (Payload p : Payload.split(kind, new JsonObject(), Payload.TO_SERVER_CHUNK)) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(p);
        }
    }

    public static int edit(JsonArray ops, Consumer<ResultPacket> callback) {
        return edit(ops, callback, true);
    }

    public static int edit(JsonArray ops, Consumer<ResultPacket> callback, boolean undoable) {
        int id = nextRequest++;
        CALLBACKS.put(id, r -> {
            if (undoable && r.ok() && lastPatch != null && patchBefore != null) {
                remember(patchBefore, lastPatch);
            }
            if (callback != null) {
                callback.accept(r);
            }
        });
        for (Payload p : Payload.split(Payload.EDIT, EditOps.request(id, ops), Payload.TO_SERVER_CHUNK)) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(p);
        }
        return id;
    }

    public static void onResult(ResultPacket r) {
        Consumer<ResultPacket> cb = CALLBACKS.remove(r.request());
        if (cb != null) {
            cb.accept(r);
        }
        lastPatch = null;
        patchBefore = null;
    }

    private static <T> T changedFrom(T before, T after, Function<T, Object> json, Function<T, T> copy, boolean[] differs) {
        differs[0] = false;
        if (before == null || after == null) {
            differs[0] = before != after;
        } else {
            differs[0] = !json.apply(before).equals(json.apply(after));
        }
        if (before == null) {
            return null;
        }
        return copy.apply(before);
    }

    private static void remember(AtlasDefinition before, JsonObject patch) {
        Undo u = new Undo();
        boolean[] differs = new boolean[1];
        JsonObject nodes = patch.getAsJsonObject("nodes");
        if (nodes != null) {
            for (String id : nodes.keySet()) {
                SkillNode b = changedFrom(before.nodes.get(id), baseline.nodes.get(id), SkillNode::toJson, SkillNode::copy, differs);
                if (differs[0]) {
                    u.nodes.put(id, b);
                }
            }
        }
        if (patch.has("meta") && patch.get("meta").getAsBoolean()) {
            Set<String> ids = new LinkedHashSet<>(before.categories.keySet());
            ids.addAll(baseline.categories.keySet());
            for (String id : ids) {
                Category b = changedFrom(before.categories.get(id), baseline.categories.get(id), Category::toJson, Category::copy, differs);
                if (differs[0]) {
                    u.categories.put(id, b);
                }
            }
            ids = new LinkedHashSet<>(before.pools.keySet());
            ids.addAll(baseline.pools.keySet());
            for (String id : ids) {
                PointPool b = changedFrom(before.pools.get(id), baseline.pools.get(id), PointPool::toJson, PointPool::copy, differs);
                if (differs[0]) {
                    u.pools.put(id, b);
                }
            }
            List<String> order = new ArrayList<>(before.categories.keySet());
            if (!order.equals(new ArrayList<>(baseline.categories.keySet()))) {
                u.order = order;
            }
        }
        JsonObject texts = patch.getAsJsonObject("texts");
        if (texts != null) {
            u.keys.addAll(texts.keySet());
        }
        AtlasDefinition restored = new AtlasDefinition();
        u.nodes.forEach((id, n) -> {
            if (n != null) {
                restored.nodes.put(id, n);
            }
        });
        u.categories.forEach((id, c) -> {
            if (c != null) {
                restored.categories.put(id, c);
            }
        });
        u.pools.forEach((id, p) -> {
            if (p != null) {
                restored.pools.put(id, p);
            }
        });
        AtlasTexts.collect(restored, u.keys);
        for (String key : u.keys) {
            u.texts.set(key, before.texts.entries(key));
        }
        if (u.isEmpty()) {
            return;
        }
        UNDO.addLast(u);
        while (UNDO.size() > MAX_UNDO) {
            UNDO.removeFirst();
        }
        SkillAtlas.LOGGER.debug("Recorded undo step: {} nodes, {} categories, {} pools, {} text keys, {} steps stored", u.nodes.size(), u.categories.size(), u.pools.size(), u.keys.size(), UNDO.size());
    }

    public static boolean canUndo() {
        return !UNDO.isEmpty();
    }

    public static boolean undo(Consumer<ResultPacket> callback) {
        Undo u = UNDO.pollLast();
        if (u == null) {
            return false;
        }
        AtlasDefinition cur = baseline;
        JsonArray ops = new JsonArray();
        u.pools.forEach((id, p) -> {
            if (p != null) {
                ops.add(EditOps.poolOp(p, !cur.pools.containsKey(id)));
            }
        });
        u.categories.forEach((id, c) -> {
            if (c != null) {
                ops.add(EditOps.categoryOp(c, !cur.categories.containsKey(id)));
            }
        });
        if (u.order != null) {
            ops.add(EditOps.orderCategoriesOp(u.order));
        }
        List<String> removed = new ArrayList<>();
        u.nodes.forEach((id, n) -> {
            if (n != null) {
                ops.add(EditOps.nodeOp(n, !cur.nodes.containsKey(id)));
            } else if (cur.nodes.containsKey(id)) {
                removed.add(id);
            }
        });
        if (!removed.isEmpty()) {
            ops.add(EditOps.deleteNodesOp(removed));
        }
        u.categories.forEach((id, c) -> {
            if (c == null && cur.categories.containsKey(id)) {
                ops.add(EditOps.deleteOp("delete_category", id));
            }
        });
        u.pools.forEach((id, p) -> {
            if (p == null && cur.pools.containsKey(id)) {
                ops.add(EditOps.deleteOp("delete_pool", id));
            }
        });
        if (!u.keys.isEmpty()) {
            ops.add(EditOps.textsOp(u.texts, u.keys));
        }
        SkillAtlas.LOGGER.debug("Undoing edit with {} ops, {} steps left", ops.size(), UNDO.size());
        edit(ops, callback, false);
        return true;
    }

    public static void learn(String node) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new LearnPacket(node));
    }

    public static List<SkillNode> nodesIn(String cat) {
        return definition.nodesIn(cat);
    }
}
