package com.hp.skillatlas.kubejs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import com.hp.skillatlas.kubejs.KubeJSCompat;

public record NavigationPacket(int action, String target, long revision) implements CustomPacketPayload {
    public static final Type<NavigationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KubeJSCompat.NAMESPACE, "navigation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NavigationPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), NavigationPacket::decode);
    @Override public Type<NavigationPacket> type() { return TYPE; }
    public static void encode(NavigationPacket p, FriendlyByteBuf buf) {
        if (p.action < 0 || p.action > 1) throw new IllegalArgumentException("Invalid navigation action");
        buf.writeByte(p.action); buf.writeUtf(p.target, 64); buf.writeLong(p.revision);
    }
    public static NavigationPacket decode(FriendlyByteBuf buf) {
        int action = buf.readUnsignedByte(); if (action > 1) throw new IllegalArgumentException("Invalid navigation action");
        return new NavigationPacket(action, buf.readUtf(64), buf.readLong());
    }
    public static void handle(NavigationPacket p, IPayloadContext context) { com.hp.skillatlas.kubejs.client.ClientNavigation.receive(p); }
}
