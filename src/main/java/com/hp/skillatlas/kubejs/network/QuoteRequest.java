package com.hp.skillatlas.kubejs.network;

import com.hp.skillatlas.kubejs.*;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuoteRequest(boolean learn, String node, String token) implements CustomPacketPayload {
    public static final Type<QuoteRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KubeJSCompat.NAMESPACE, "quote_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuoteRequest> STREAM_CODEC = StreamCodec.of((buffer, packet) -> encode(packet, buffer), QuoteRequest::decode);
    @Override public Type<QuoteRequest> type() { return TYPE; }
    public static void encode(QuoteRequest p, FriendlyByteBuf buf) { buf.writeBoolean(p.learn); buf.writeUtf(p.node, 64); buf.writeUtf(p.token, 36); }
    public static QuoteRequest decode(FriendlyByteBuf buf) { return new QuoteRequest(buf.readBoolean(), buf.readUtf(64), buf.readUtf(36)); }
    public static void handle(QuoteRequest p, IPayloadContext context) {
        var player = context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null || !com.hp.skillatlas.data.Json.validId(p.node)) return;
        if (p.learn) Quotes.learn(player, p.node, p.token);
        else {
            LearningQuote quote = Quotes.request(player, p.node);
            if (quote != null) QuoteNetwork.reply(player, quote);
        }
    }
}
