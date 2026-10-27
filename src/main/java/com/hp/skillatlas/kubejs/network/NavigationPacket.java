package com.hp.skillatlas.kubejs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record NavigationPacket(int action, String target, long revision) {
    public static void encode(NavigationPacket p, FriendlyByteBuf buf) {
        if (p.action < 0 || p.action > 1) throw new IllegalArgumentException("Invalid navigation action");
        buf.writeByte(p.action); buf.writeUtf(p.target, 64); buf.writeLong(p.revision);
    }
    public static NavigationPacket decode(FriendlyByteBuf buf) {
        int action = buf.readUnsignedByte(); if (action > 1) throw new IllegalArgumentException("Invalid navigation action");
        return new NavigationPacket(action, buf.readUtf(64), buf.readLong());
    }
    public static void handle(NavigationPacket p, Supplier<NetworkEvent.Context> context) { DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.hp.skillatlas.kubejs.client.ClientNavigation.receive(p)); }
}
