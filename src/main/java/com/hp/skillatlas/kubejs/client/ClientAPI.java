package com.hp.skillatlas.kubejs.client;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.screen.NodeTooltip;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.kubejs.Outcome;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class ClientAPI {
    private ClientAPI() {}
    private static void thread() { if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Client API requires the client main thread"); }
    public static boolean isReady() { thread(); return Minecraft.getInstance().player != null && ClientNavigation.ready(); }
    public static int getLevel(String id) { thread(); return isReady() && ClientAtlas.definition.nodes.containsKey(id) ? ClientAtlas.progress.level(id) : 0; }
    public static Outcome open() { return open(""); }
    public static Outcome open(String category) { return ClientNavigation.request(0, category); }
    public static Outcome focus(String node) { return ClientNavigation.request(1, node); }
    public static List<Component> tooltip(String id) {
        thread(); if (!isReady()) return List.of();
        var node = ClientAtlas.definition.nodes.get(id);
        if (node == null || !AtlasLogic.visible(ClientAtlas.definition, node, ClientAtlas.progress)) return List.of();
        return NodeTooltip.lines(node).stream().map(Component::copy).map(c -> (Component)c).toList();
    }
}
