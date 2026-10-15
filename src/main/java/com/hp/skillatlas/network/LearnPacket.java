package com.hp.skillatlas.network;

import com.hp.skillatlas.server.AtlasServer;
import com.hp.skillatlas.SkillAtlas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record LearnPacket(String node) implements CustomPacketPayload {
    public static final Type<LearnPacket> TYPE = new Type<>(SkillAtlas.id("learn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LearnPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), LearnPacket::decode);

    @Override
    public Type<LearnPacket> type() {
        return TYPE;
    }
    public static void encode(LearnPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.node, 64);
    }

    public static LearnPacket decode(FriendlyByteBuf buf) {
        return new LearnPacket(buf.readUtf(64));
    }

    public static void handle(LearnPacket p, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            AtlasServer.learn(player, p.node);
        }
    }
}
