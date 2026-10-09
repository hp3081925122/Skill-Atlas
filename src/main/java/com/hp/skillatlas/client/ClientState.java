package com.hp.skillatlas.client;

import com.google.gson.*;
import com.hp.skillatlas.core.StrictJson;
import com.hp.skillatlas.model.*;
import com.hp.skillatlas.network.AtlasNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class ClientState {
    public static Definition definition = Definition.defaults();
    public static PlayerProgress progress = new PlayerProgress();
    public static boolean canEdit;
    public static AtlasScreen screen;
    public static final Map<String, List<String>> registries = new HashMap<>();
    public static void request(String operation) { request(operation, new JsonObject()); }
    public static void request(String operation, JsonObject o) { o.addProperty("op", operation); AtlasNetwork.request(Definition.JSON.toJson(o)); }
    public static void receive(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject(); String op = o.get("op").getAsString();
        switch (op) {
            case "snapshot" -> {
                definition = Definition.JSON.fromJson(o.get("definition"), Definition.class); progress = Definition.JSON.fromJson(o.get("progress"), PlayerProgress.class); canEdit = o.get("editor").getAsBoolean();
                registries.clear(); if (o.has("registries")) o.getAsJsonObject("registries").entrySet().forEach(e -> { List<String> values = new ArrayList<>(); e.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsString())); registries.put(e.getKey(), values); });
                if (screen != null) screen.receivedDefinition();
                if (o.get("open").getAsBoolean()) { screen = new AtlasScreen(); Minecraft.getInstance().setScreen(screen); }
            }
            case "progress" -> { progress = Definition.JSON.fromJson(o.get("progress"), PlayerProgress.class); }
            case "draft" -> { if (screen != null) screen.receiveDraft(Definition.JSON.fromJson(o.get("definition"), Definition.class)); }
            case "status" -> {
                String code = o.get("code").getAsString(), detail = o.get("detail").getAsString();
                Component message = Component.translatable("skill_atlas.status." + code);
                if (!detail.isBlank()) message = message.copy().append(": " + detail);
                if (screen != null) screen.status(message, code);
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(message, true);
            }
        }
    }
    public static void clear() { definition = Definition.defaults(); progress = new PlayerProgress(); canEdit = false; registries.clear(); screen = null; AtlasNetwork.clearClient(); AtlasScreen.clearViews(); }
}
