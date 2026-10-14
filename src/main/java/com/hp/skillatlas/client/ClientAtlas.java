package com.hp.skillatlas.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hp.skillatlas.ItemCosts;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.EditOps;
import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.network.LearnPacket;
import com.hp.skillatlas.network.Payload;
import com.hp.skillatlas.network.ResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class ClientAtlas {
    public static final class View {
        public float panX;
        public float panY;
        public float zoom = 1.0F;
        public boolean initialized;
    }

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
            changed();
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
            AtlasDefinition next = definition.copy();
            EditOps.applyPatch(next, o);
            definition = next;
            revision = rev;
            changed();
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
            AtlasNetwork.CHANNEL.sendToServer(p);
        }
    }

    public static int edit(JsonArray ops, Consumer<ResultPacket> callback) {
        int id = nextRequest++;
        if (callback != null) {
            CALLBACKS.put(id, callback);
        }
        for (Payload p : Payload.split(Payload.EDIT, EditOps.request(id, ops), Payload.TO_SERVER_CHUNK)) {
            AtlasNetwork.CHANNEL.sendToServer(p);
        }
        return id;
    }

    public static void onResult(ResultPacket r) {
        Consumer<ResultPacket> cb = CALLBACKS.remove(r.request());
        if (cb != null) {
            cb.accept(r);
        }
    }

    public static void learn(String node) {
        AtlasNetwork.CHANNEL.sendToServer(new LearnPacket(node));
    }

    public static List<SkillNode> nodesIn(String cat) {
        return definition.nodesIn(cat);
    }
}
