package com.hp.skillatlas.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AtlasNetwork {
    private AtlasNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2612-1");
        registrar.playBidirectional(Payload.TYPE, Payload.STREAM_CODEC, Payload::handle);
        registrar.playToServer(LearnPacket.TYPE, LearnPacket.STREAM_CODEC, LearnPacket::handle);
        registrar.playToClient(ProgressPacket.TYPE, ProgressPacket.STREAM_CODEC);
        registrar.playToClient(ResultPacket.TYPE, ResultPacket.STREAM_CODEC);
        registrar.playToClient(EnchantingPreviewPacket.TYPE, EnchantingPreviewPacket.STREAM_CODEC);
    }
}