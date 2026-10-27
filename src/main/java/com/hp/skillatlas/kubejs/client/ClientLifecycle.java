package com.hp.skillatlas.kubejs.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ClientLifecycle {
    public static void register() { MinecraftForge.EVENT_BUS.addListener(ClientLifecycle::tick); }
    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) { ClientQuotes.clear(); ClientTooltips.clear(); }
        else { ClientQuotes.tick(); ClientTooltips.tick(); }
        ClientNavigation.tick();
    }
}
