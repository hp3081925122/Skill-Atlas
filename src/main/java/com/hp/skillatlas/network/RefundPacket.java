package com.hp.skillatlas.network;

import com.hp.skillatlas.server.AtlasServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import com.hp.skillatlas.SkillAtlas;


public record RefundPacket(String node) implements CustomPacketPayload {
    public static final Type<RefundPacket> TYPE = new Type<>(SkillAtlas.id("refund"));
    public static final StreamCodec<FriendlyByteBuf, RefundPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), RefundPacket::decode);
    @Override public Type<RefundPacket> type() { return TYPE; }

    public static void encode(RefundPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.node, 64);
    }

    public static RefundPacket decode(FriendlyByteBuf buf) {
        return new RefundPacket(buf.readUtf(64));
    }


    public static void handle(RefundPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            AtlasServer.refund(player, packet.node);
        }
    }
}
