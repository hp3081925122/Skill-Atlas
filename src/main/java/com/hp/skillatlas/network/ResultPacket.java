package com.hp.skillatlas.network;

import com.hp.skillatlas.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ResultPacket(int request, boolean ok, String key, List<String> args) {
    public static final int LEARN = -1;

    public static void encode(ResultPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.request);
        buf.writeBoolean(p.ok);
        buf.writeUtf(p.key, 256);
        buf.writeVarInt(p.args.size());
        for (String s : p.args) {
            buf.writeUtf(s, 1024);
        }
    }

    public static ResultPacket decode(FriendlyByteBuf buf) {
        int request = buf.readVarInt();
        boolean ok = buf.readBoolean();
        String key = buf.readUtf(256);
        int size = Math.min(16, buf.readVarInt());
        List<String> args = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            args.add(buf.readUtf(1024));
        }
        return new ResultPacket(request, ok, key, args);
    }

    public static void handle(ResultPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.result(p));
    }
}
