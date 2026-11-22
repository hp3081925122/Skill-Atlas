package com.hp.skillatlas.kubejs.client;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.screen.AtlasScreen;
import com.hp.skillatlas.kubejs.*;
import com.hp.skillatlas.kubejs.network.NavigationPacket;
import net.minecraft.client.Minecraft;
import java.util.Map;

public final class ClientNavigation {
    private record Pending(NavigationPacket packet, long expires, boolean replaceScreen) {}
    private static Pending pending;
    private static long ticks, lastLocal = -100, lastResync = -100;
    private static boolean progressReady;
    private static Object connection;
    private ClientNavigation() {}
    public static boolean ready() { return ClientAtlas.ready && progressReady; }
    public static void progress() { session(); progressReady = true; }
    public static void receive(NavigationPacket packet) { session(); pending = new Pending(packet, ticks + 200, false); }
    private static void session() {
        Object current = Minecraft.getInstance().getConnection();
        if (current != connection) { if (connection != null) { clear(); ClientTooltips.clear(); } connection = current; }
    }
    public static Outcome request(int action, String target) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) throw new IllegalStateException("Client navigation requires the client main thread");
        session();
        if (ClientTooltips.evaluating()) return Outcome.fail("reentrant_operation");
        if (mc.player == null) return Outcome.fail("no_player");
        if (target == null || target.length() > 64 || action < 0 || action > 1 || !target.isEmpty() && !com.hp.skillatlas.data.Json.validId(target)) return Outcome.fail("invalid_argument");
        if (ready()) { Outcome valid = Navigation.validate(ClientAtlas.definition, ClientAtlas.progress, action, target); if (!valid.ok) return valid; }
        if (ticks - lastLocal < 5) return Outcome.fail("rate_limited");
        lastLocal = ticks; pending = new Pending(new NavigationPacket(action, target, ready() ? ClientAtlas.revision : -1), ticks + 200, true);
        if (ready()) return apply();
        resync(); Outcome result = Outcome.ok(); result.detail = Map.of("state", "queued"); return result;
    }
    private static void resync() { if (ticks - lastResync >= 20) { lastResync = ticks; ClientAtlas.requestResync(); } }
    public static void tick() {
        session(); ticks++; if (pending == null) return;
        if (ticks >= pending.expires) { pending = null; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ready()) { if (mc.player != null) resync(); return; }
        if (pending.packet.revision() > ClientAtlas.revision) { resync(); return; }
        if (pending.packet.revision() >= 0 && pending.packet.revision() < ClientAtlas.revision) { pending = null; return; }
        if (!pending.replaceScreen && mc.screen != null && AtlasScreen.current() == null) return;
        Outcome result = apply(); if (!result.ok) mc.player.sendSystemMessage(result.message);
    }
    private static Outcome apply() {
        Pending action = pending; pending = null;
        Outcome valid = Navigation.validate(ClientAtlas.definition, ClientAtlas.progress, action.packet.action(), action.packet.target());
        if (!valid.ok) return valid;
        Minecraft mc = Minecraft.getInstance(); AtlasScreen screen = AtlasScreen.current();
        if (screen == null) { screen = new AtlasScreen(); mc.setScreen(screen); }
        if (action.packet.action() == 1) screen.main().jumpTo(ClientAtlas.definition.nodes.get(action.packet.target()));
        else if (!action.packet.target().isEmpty()) screen.main().selectCategory(action.packet.target());
        Outcome result = Outcome.ok(); result.detail = Map.of("state", "opened"); return result;
    }
    public static void cancel() { pending = null; }
    public static void clear() { pending = null; ticks = 0; progressReady = false; lastLocal = -100; lastResync = -100; connection = null; }
}
