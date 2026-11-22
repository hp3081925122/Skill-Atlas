package com.hp.skillatlas.kubejs.network;

import com.hp.skillatlas.kubejs.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class QuoteNetwork {
    private QuoteNetwork() {}
    public static void register(IEventBus bus) { bus.addListener(QuoteNetwork::payloads); }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2").optional();
        registrar.playToServer(QuoteRequest.TYPE, QuoteRequest.STREAM_CODEC, QuoteRequest::handle);
        registrar.playToClient(QuoteResponse.TYPE, QuoteResponse.STREAM_CODEC, QuoteResponse::handle);
        registrar.playToClient(NavigationPacket.TYPE, NavigationPacket.STREAM_CODEC, NavigationPacket::handle);
    }
    public static void navigate(net.minecraft.server.level.ServerPlayer player, NavigationPacket packet) { if (player.connection.hasChannel(NavigationPacket.TYPE)) PacketDistributor.sendToPlayer(player, packet); }
    public static void send(QuoteRequest request) { PacketDistributor.sendToServer(request); }
    public static void reply(net.minecraft.server.level.ServerPlayer player, LearningQuote quote) { if (player.connection.hasChannel(QuoteResponse.TYPE)) PacketDistributor.sendToPlayer(player, new QuoteResponse(quote)); }
}