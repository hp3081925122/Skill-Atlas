package com.hp.skillatlas.network;

import com.hp.skillatlas.server.AtlasServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record LearnPacket(String node) {
    public static void encode(LearnPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.node, 64);
    }

    public static LearnPacket decode(FriendlyByteBuf buf) {
        return new LearnPacket(buf.readUtf(64));
    }

    public static void handle(LearnPacket p, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getSender() != null) {
            AtlasServer.learn(ctx.get().getSender(), p.node);
        }
    }
}
