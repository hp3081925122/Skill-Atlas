package com.hp.skillatlas.network;

import com.hp.skillatlas.server.AtlasServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RefundPacket(String node) {
    public static void encode(RefundPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.node, 64);
    }

    public static RefundPacket decode(FriendlyByteBuf buf) {
        return new RefundPacket(buf.readUtf(64));
    }

    public static void handle(RefundPacket p, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getSender() != null) {
            AtlasServer.refund(ctx.get().getSender(), p.node);
        }
    }
}
