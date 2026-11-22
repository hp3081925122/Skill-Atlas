package com.hp.skillatlas.kubejs.network;

import com.google.gson.JsonParser;
import com.hp.skillatlas.kubejs.LearningQuote;
import com.hp.skillatlas.kubejs.client.ClientQuotes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.connection.ConnectionType;
import com.hp.skillatlas.kubejs.KubeJSCompat;
import com.hp.skillatlas.server.AtlasServer;

public record QuoteResponse(LearningQuote quote) implements CustomPacketPayload {
    public static final Type<QuoteResponse> TYPE = new Type<>(Identifier.fromNamespaceAndPath(KubeJSCompat.NAMESPACE, "quote_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuoteResponse> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), QuoteResponse::decode);
    @Override public Type<QuoteResponse> type() { return TYPE; }
    public static void encode(QuoteResponse p, RegistryFriendlyByteBuf buf) {
        int start = buf.writerIndex();
        LearningQuote q = p.quote;
        buf.writeUtf(q.nodeId(), 64); buf.writeLong(q.revision()); buf.writeVarInt(q.level()); buf.writeUtf(q.token(), 36); buf.writeLong(q.expires());
        buf.writeBoolean(q.allowed()); buf.writeUtf(q.code(), 64); ComponentSerialization.STREAM_CODEC.encode(buf, q.message()); buf.writeUtf(q.cost().toString(), 1048576); bounded(buf, start);
        buf.writeVarInt(q.conditions().size());
        for (var c : q.conditions()) { buf.writeBoolean(c.met()); ComponentSerialization.STREAM_CODEC.encode(buf, c.description()); bounded(buf, start); }
        writeLines(buf, q.currentRewards(), start); writeLines(buf, q.nextRewards(), start); bounded(buf, start);
    }
    public static QuoteResponse decode(RegistryFriendlyByteBuf buf) {
        String node = buf.readUtf(64); long revision = buf.readLong(); int level = buf.readVarInt(); String token = buf.readUtf(36); long expires = buf.readLong();
        boolean allowed = buf.readBoolean(); String code = buf.readUtf(64); Component message = ComponentSerialization.STREAM_CODEC.decode(buf); var cost = JsonParser.parseString(buf.readUtf(1048576)).getAsJsonObject();
        int count = size(buf, 64); List<LearningQuote.ConditionLine> conditions = new ArrayList<>();
        for (int i = 0; i < count; i++) conditions.add(new LearningQuote.ConditionLine(buf.readBoolean(), ComponentSerialization.STREAM_CODEC.decode(buf)));
        return new QuoteResponse(new LearningQuote(node, revision, level, token, expires, allowed, code, message, cost, List.copyOf(conditions), readLines(buf), readLines(buf)));
    }
    private static void writeLines(RegistryFriendlyByteBuf buf, List<Component> lines, int start) { buf.writeVarInt(lines.size()); for (Component line : lines) { ComponentSerialization.STREAM_CODEC.encode(buf, line); bounded(buf, start); } }
    private static List<Component> readLines(RegistryFriendlyByteBuf buf) { int count = size(buf, 32); List<Component> result = new ArrayList<>(); for (int i = 0; i < count; i++) result.add(ComponentSerialization.STREAM_CODEC.decode(buf)); return List.copyOf(result); }
    private static int size(RegistryFriendlyByteBuf buf, int max) { int n = buf.readVarInt(); if (n < 0 || n > max) throw new IllegalArgumentException("Invalid quote list length"); return n; }
    private static void bounded(RegistryFriendlyByteBuf buf, int start) { if (buf.writerIndex() - start > 1048512) throw new IllegalArgumentException("Quote packet is too large"); }
    public static void validate(LearningQuote quote) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), AtlasServer.server() == null ? RegistryAccess.EMPTY : AtlasServer.server().registryAccess(), ConnectionType.NEOFORGE);
        try { encode(new QuoteResponse(quote), buffer); } finally { buffer.release(); }
    }
    public static void handle(QuoteResponse p, IPayloadContext context) { ClientQuotes.receive(p.quote); }
}
