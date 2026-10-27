package com.hp.skillatlas.kubejs.network;

import com.google.gson.JsonParser;
import com.hp.skillatlas.kubejs.LearningQuote;
import com.hp.skillatlas.kubejs.client.ClientQuotes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record QuoteResponse(LearningQuote quote) {
    public static void encode(QuoteResponse p, FriendlyByteBuf buf) {
        int start = buf.writerIndex();
        LearningQuote q = p.quote;
        buf.writeUtf(q.nodeId(), 64); buf.writeLong(q.revision()); buf.writeVarInt(q.level()); buf.writeUtf(q.token(), 36); buf.writeLong(q.expires());
        buf.writeBoolean(q.allowed()); buf.writeUtf(q.code(), 64); buf.writeComponent(q.message()); buf.writeUtf(q.cost().toString(), 1048576); bounded(buf, start);
        buf.writeVarInt(q.conditions().size());
        for (var c : q.conditions()) { buf.writeBoolean(c.met()); buf.writeComponent(c.description()); bounded(buf, start); }
        writeLines(buf, q.currentRewards(), start); writeLines(buf, q.nextRewards(), start); bounded(buf, start);
    }
    public static QuoteResponse decode(FriendlyByteBuf buf) {
        String node = buf.readUtf(64); long revision = buf.readLong(); int level = buf.readVarInt(); String token = buf.readUtf(36); long expires = buf.readLong();
        boolean allowed = buf.readBoolean(); String code = buf.readUtf(64); Component message = buf.readComponent(); var cost = JsonParser.parseString(buf.readUtf(1048576)).getAsJsonObject();
        int count = size(buf, 64); List<LearningQuote.ConditionLine> conditions = new ArrayList<>();
        for (int i = 0; i < count; i++) conditions.add(new LearningQuote.ConditionLine(buf.readBoolean(), buf.readComponent()));
        return new QuoteResponse(new LearningQuote(node, revision, level, token, expires, allowed, code, message, cost, List.copyOf(conditions), readLines(buf), readLines(buf)));
    }
    private static void writeLines(FriendlyByteBuf buf, List<Component> lines, int start) { buf.writeVarInt(lines.size()); for (Component line : lines) { buf.writeComponent(line); bounded(buf, start); } }
    private static List<Component> readLines(FriendlyByteBuf buf) { int count = size(buf, 32); List<Component> result = new ArrayList<>(); for (int i = 0; i < count; i++) result.add(buf.readComponent()); return List.copyOf(result); }
    private static int size(FriendlyByteBuf buf, int max) { int n = buf.readVarInt(); if (n < 0 || n > max) throw new IllegalArgumentException("Invalid quote list length"); return n; }
    private static void bounded(FriendlyByteBuf buf, int start) { if (buf.writerIndex() - start > 1048512) throw new IllegalArgumentException("Quote packet is too large"); }
    public static void validate(LearningQuote quote) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { encode(new QuoteResponse(quote), buffer); } finally { buffer.release(); }
    }
    public static void handle(QuoteResponse p, Supplier<NetworkEvent.Context> context) { DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientQuotes.receive(p.quote)); }
}
