package com.hp.skillatlas.network;

import com.hp.skillatlas.client.ClientPackets;
import net.minecraft.nbt.CompoundTag;
import com.hp.skillatlas.SkillAtlas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record ProgressPacket(CompoundTag progress, boolean canEdit) implements CustomPacketPayload {
    public static final Type<ProgressPacket> TYPE = new Type<>(SkillAtlas.id("progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), ProgressPacket::decode);

    @Override
    public Type<ProgressPacket> type() {
        return TYPE;
    }
    public static void encode(ProgressPacket p, FriendlyByteBuf buf) {
        buf.writeNbt(p.progress);
        buf.writeBoolean(p.canEdit);
    }

    public static ProgressPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            tag = new CompoundTag();
        }
        return new ProgressPacket(tag, buf.readBoolean());
    }

    public static void handle(ProgressPacket p, IPayloadContext context) {
        ClientPackets.progress(p);
    }
}
