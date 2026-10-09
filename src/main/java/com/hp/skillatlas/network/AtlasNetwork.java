package com.hp.skillatlas.network;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.core.ChunkAssembler;
import com.hp.skillatlas.client.ClientState;
import com.hp.skillatlas.server.AtlasService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AtlasNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(SkillAtlas.ID, "main"), () -> "1", "1"::equals, "1"::equals);
    private static final ChunkAssembler SERVER = new ChunkAssembler();
    private static final ChunkAssembler CLIENT = new ChunkAssembler();
    private static final UUID CLIENT_ID = new UUID(0, 0);
    private static final Map<UUID, Long> LAST_START = new HashMap<>();
    public record Fragment(long id, int index, int total, byte[] bytes) {}
    public static void register() {
        CHANNEL.registerMessage(0, Fragment.class,
            (m, b) -> { b.writeLong(m.id); b.writeVarInt(m.index); b.writeVarInt(m.total); b.writeByteArray(m.bytes); },
            b -> new Fragment(b.readLong(), b.readVarInt(), b.readVarInt(), b.readByteArray(ChunkAssembler.CHUNK)),
            (m, context) -> {
                var ctx = context.get(); ServerPlayer sender = ctx.getSender();
                ctx.enqueueWork(() -> {
                    try {
                        if (sender != null) {
                            long now = System.currentTimeMillis();
                            if (m.index == 0) {
                                Long last = LAST_START.put(sender.getUUID(), now);
                                if (last != null && now - last < 100) return;
                            }
                            if (m.total > 1 && !sender.hasPermissions(2)) throw new IllegalArgumentException("Large requests require administrator permission");
                            String text = SERVER.accept(sender.getUUID(), m.id, m.index, m.total, m.bytes, now);
                            if (text != null && AtlasService.get() != null) AtlasService.get().request(sender, text);
                        } else {
                            String text = CLIENT.accept(CLIENT_ID, m.id, m.index, m.total, m.bytes, System.currentTimeMillis());
                            if (text != null) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientState.receive(text));
                        }
                    } catch (Exception ex) {
                        SkillAtlas.LOGGER.warn("Rejected Skill Atlas packet: {}", ex.getMessage());
                        if (sender != null) AtlasService.get().status(sender, "error", ex.getMessage());
                    }
                }); ctx.setPacketHandled(true);
            });
    }
    private static List<Fragment> fragments(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ChunkAssembler.MAX_BYTES) throw new IllegalArgumentException("Packet exceeds transfer limit");
        int total = Math.max(1, (bytes.length + ChunkAssembler.CHUNK - 1) / ChunkAssembler.CHUNK);
        long id = java.util.concurrent.ThreadLocalRandom.current().nextLong(); List<Fragment> result = new ArrayList<>();
        for (int i = 0; i < total; i++) result.add(new Fragment(id, i, total, Arrays.copyOfRange(bytes, i * ChunkAssembler.CHUNK, Math.min(bytes.length, (i + 1) * ChunkAssembler.CHUNK))));
        return result;
    }
    public static void send(ServerPlayer p, String text) { fragments(text).forEach(f -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), f)); }
    public static void request(String text) { fragments(text).forEach(CHANNEL::sendToServer); }
    public static void disconnect(UUID id) { SERVER.remove(id); LAST_START.remove(id); }
    public static void clearClient() { CLIENT.clear(); }
    public static void clearServer() { SERVER.clear(); LAST_START.clear(); }
}
