package com.hp.skillatlas.kubejs.network;

import com.hp.skillatlas.kubejs.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record QuoteRequest(boolean learn, String node, String token) {
    public static void encode(QuoteRequest p, FriendlyByteBuf buf) { buf.writeBoolean(p.learn); buf.writeUtf(p.node, 64); buf.writeUtf(p.token, 36); }
    public static QuoteRequest decode(FriendlyByteBuf buf) { return new QuoteRequest(buf.readBoolean(), buf.readUtf(64), buf.readUtf(36)); }
    public static void handle(QuoteRequest p, Supplier<NetworkEvent.Context> context) {
        var player = context.get().getSender();
        if (player == null || !com.hp.skillatlas.data.Json.validId(p.node)) return;
        if (p.learn) Quotes.learn(player, p.node, p.token);
        else {
            LearningQuote quote = Quotes.request(player, p.node);
            if (quote != null) QuoteNetwork.reply(player, quote);
        }
    }
}
