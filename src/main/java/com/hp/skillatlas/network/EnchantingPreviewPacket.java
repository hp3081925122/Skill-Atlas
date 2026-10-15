package com.hp.skillatlas.network;

import com.hp.skillatlas.client.ClientPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record EnchantingPreviewPacket(int menuId, CompoundTag preview) {
    public static void encode(EnchantingPreviewPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.menuId);
        buffer.writeNbt(packet.preview);
    }

    public static EnchantingPreviewPacket decode(FriendlyByteBuf buffer) {
        int menuId = buffer.readVarInt();
        CompoundTag preview = buffer.readNbt();
        return new EnchantingPreviewPacket(menuId, preview == null ? new CompoundTag() : preview);
    }

    public static void handle(EnchantingPreviewPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.enchantingPreview(packet));
    }
}
