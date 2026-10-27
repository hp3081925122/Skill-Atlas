package com.hp.skillatlas.kubejs.network;

import com.hp.skillatlas.kubejs.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class QuoteNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.tryParse(KubeJSCompat.NAMESPACE + ":quotes"), () -> "2", QuoteNetwork::accepts, QuoteNetwork::accepts);
    private QuoteNetwork() {}
    private static boolean accepts(String version) { return "2".equals(version) || NetworkRegistry.ABSENT.version().equals(version); }
    public static void register() {
        CHANNEL.messageBuilder(QuoteRequest.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(QuoteRequest::encode).decoder(QuoteRequest::decode).consumerMainThread(QuoteRequest::handle).add();
        CHANNEL.messageBuilder(QuoteResponse.class, 1, NetworkDirection.PLAY_TO_CLIENT).encoder(QuoteResponse::encode).decoder(QuoteResponse::decode).consumerMainThread(QuoteResponse::handle).add();
        CHANNEL.messageBuilder(NavigationPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT).encoder(NavigationPacket::encode).decoder(NavigationPacket::decode).consumerMainThread(NavigationPacket::handle).add();
    }
    public static void send(QuoteRequest request) { CHANNEL.sendToServer(request); }
    public static void reply(net.minecraft.server.level.ServerPlayer player, LearningQuote quote) { if (CHANNEL.isRemotePresent(player.connection.connection)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new QuoteResponse(quote)); }
    public static void navigate(net.minecraft.server.level.ServerPlayer player, NavigationPacket packet) { if (CHANNEL.isRemotePresent(player.connection.connection)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }
}
