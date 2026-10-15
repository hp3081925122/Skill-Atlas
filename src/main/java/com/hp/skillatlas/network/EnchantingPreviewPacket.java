package com.hp.skillatlas.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import com.hp.skillatlas.SkillAtlas;


public record EnchantingPreviewPacket(int menuId, CompoundTag preview) implements CustomPacketPayload {
    public static final Type<EnchantingPreviewPacket> TYPE = new Type<>(SkillAtlas.id("enchanting_preview"));
    public static final StreamCodec<FriendlyByteBuf, EnchantingPreviewPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), EnchantingPreviewPacket::decode);
    @Override public Type<EnchantingPreviewPacket> type() { return TYPE; }

    public static void encode(EnchantingPreviewPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.menuId);
        buffer.writeNbt(packet.preview);
    }

    public static EnchantingPreviewPacket decode(FriendlyByteBuf buffer) {
        int menuId = buffer.readVarInt();
        CompoundTag preview = buffer.readNbt();
        return new EnchantingPreviewPacket(menuId, preview == null ? new CompoundTag() : preview);
    }

}
