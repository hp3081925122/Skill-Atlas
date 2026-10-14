package com.hp.skillatlas.network;

import com.hp.skillatlas.SkillAtlas;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AtlasNetwork {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(SkillAtlas.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private AtlasNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(Payload.class, id++)
                .encoder(Payload::encode).decoder(Payload::decode).consumerMainThread(Payload::handle).add();
        CHANNEL.messageBuilder(LearnPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LearnPacket::encode).decoder(LearnPacket::decode).consumerMainThread(LearnPacket::handle).add();
        CHANNEL.messageBuilder(ProgressPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProgressPacket::encode).decoder(ProgressPacket::decode).consumerMainThread(ProgressPacket::handle).add();
        CHANNEL.messageBuilder(ResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResultPacket::encode).decoder(ResultPacket::decode).consumerMainThread(ResultPacket::handle).add();
    }
}
