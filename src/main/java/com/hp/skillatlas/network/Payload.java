package com.hp.skillatlas.network;

import com.google.gson.JsonObject;
import com.hp.skillatlas.client.ClientPackets;
import com.hp.skillatlas.data.Json;
import com.hp.skillatlas.server.AtlasServer;
import com.hp.skillatlas.SkillAtlas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Payload implements CustomPacketPayload {
    public static final Type<Payload> TYPE = new Type<>(SkillAtlas.id("atlas"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), Payload::decode);
    public static final byte EDIT = 1;
    public static final byte RESYNC = 2;
    public static final byte PROGRESS = 3;
    public static final byte FULL = 1;
    public static final byte PATCH = 2;
    public static final int TO_SERVER_CHUNK = 30_000;
    public static final int TO_CLIENT_CHUNK = 900_000;
    public static final int MAX_COMPRESSED = 8 * 1024 * 1024;
    public static final int MAX_RAW = 32 * 1024 * 1024;
    private static final AtomicInteger TRANSFERS = new AtomicInteger();

    public final int transfer;
    public final byte kind;
    public final int index;
    public final int total;
    public final byte[] data;

    public Payload(int transfer, byte kind, int index, int total, byte[] data) {
        this.transfer = transfer;
        this.kind = kind;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    public static void encode(Payload p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.transfer);
        buf.writeByte(p.kind);
        buf.writeVarInt(p.index);
        buf.writeVarInt(p.total);
        buf.writeByteArray(p.data);
    }

    public static Payload decode(FriendlyByteBuf buf) {
        int transfer = buf.readVarInt();
        byte kind = buf.readByte();
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        byte[] data = buf.readByteArray(TO_CLIENT_CHUNK + 64);
        return new Payload(transfer, kind, index, total, data);
    }

    @Override
    public Type<Payload> type() {
        return TYPE;
    }

    public static void handle(Payload p, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            AtlasServer.receive(player, p);
        } else {
            ClientPackets.payload(p);
        }
    }

    public static List<Payload> split(byte kind, JsonObject json, int chunk) {
        byte[] packed = gzip(Json.COMPACT.toJson(json).getBytes(StandardCharsets.UTF_8));
        int total = Math.max(1, (packed.length + chunk - 1) / chunk);
        int transfer = TRANSFERS.incrementAndGet();
        List<Payload> out = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int from = i * chunk;
            int to = Math.min(packed.length, from + chunk);
            out.add(new Payload(transfer, kind, i, total, Arrays.copyOfRange(packed, from, to)));
        }
        return out;
    }

    public static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    public static byte[] gunzip(byte[] packed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, packed.length * 4));
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                if (out.size() + read > MAX_RAW) {
                    throw new IOException("Payload exceeds " + MAX_RAW + " bytes");
                }
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }
}
