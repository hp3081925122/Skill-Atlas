package com.hp.skillatlas.network;

import com.hp.skillatlas.client.ClientPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ProgressPacket(CompoundTag progress, boolean canEdit, boolean refund, int refundCost) {
    public static void encode(ProgressPacket p, FriendlyByteBuf buf) {
        buf.writeNbt(p.progress);
        buf.writeBoolean(p.canEdit);
        buf.writeBoolean(p.refund);
        buf.writeVarInt(p.refundCost);
    }

    public static ProgressPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            tag = new CompoundTag();
        }
        return new ProgressPacket(tag, buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(ProgressPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.progress(p));
    }
}
