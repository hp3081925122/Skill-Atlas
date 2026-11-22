package com.hp.skillatlas.kubejs.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientLifecycle {
    public static void register() { NeoForge.EVENT_BUS.addListener(ClientLifecycle::tick); }
    private static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) { ClientQuotes.clear(); ClientTooltips.clear(); }
        else { ClientQuotes.tick(); ClientTooltips.tick(); }
        ClientNavigation.tick();
    }
}
