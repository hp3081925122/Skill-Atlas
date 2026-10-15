package com.hp.skillatlas.network;

import com.hp.skillatlas.client.ClientPackets;
import com.hp.skillatlas.SkillAtlas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ResultPacket(int request, boolean ok, String key, List<String> args) implements CustomPacketPayload {
    public static final Type<ResultPacket> TYPE = new Type<>(SkillAtlas.id("result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResultPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), ResultPacket::decode);

    @Override
    public Type<ResultPacket> type() {
        return TYPE;
    }
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

    public static void handle(ResultPacket p, IPayloadContext context) {
        ClientPackets.result(p);
    }
}
