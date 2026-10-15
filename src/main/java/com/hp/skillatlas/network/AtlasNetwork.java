package com.hp.skillatlas.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AtlasNetwork {

    private AtlasNetwork() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(AtlasNetwork::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("9");
        registrar.playBidirectional(Payload.TYPE, Payload.STREAM_CODEC, Payload::handle);
        registrar.playToServer(LearnPacket.TYPE, LearnPacket.STREAM_CODEC, LearnPacket::handle);
        registrar.playToClient(ProgressPacket.TYPE, ProgressPacket.STREAM_CODEC, ProgressPacket::handle);
        registrar.playToClient(ResultPacket.TYPE, ResultPacket.STREAM_CODEC, ResultPacket::handle);
        registrar.playToClient(EnchantingPreviewPacket.TYPE, EnchantingPreviewPacket.STREAM_CODEC, EnchantingPreviewPacket::handle);
    }
}
