package com.hp.skillatlas.kubejs.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.hp.skillatlas.kubejs.network.NavigationPacket;
import com.hp.skillatlas.kubejs.network.QuoteResponse;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientLifecycle {
    public static void register(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(ClientLifecycle::tick);
        bus.addListener(ClientLifecycle::payloads);
    }
    private static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(QuoteResponse.TYPE, QuoteResponse::handle);
        event.register(NavigationPacket.TYPE, NavigationPacket::handle);
    }
    private static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) { ClientQuotes.clear(); ClientTooltips.clear(); }
        else { ClientQuotes.tick(); ClientTooltips.tick(); }
        ClientNavigation.tick();
    }
}
