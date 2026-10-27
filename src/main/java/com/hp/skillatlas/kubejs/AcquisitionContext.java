package com.hp.skillatlas.kubejs;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;

public final class AcquisitionContext {
    public final ServerPlayer player;
    public final String typeId, poolId;
    public final Map<String, Object> payload;
    public final long units;
    public AcquisitionContext(ServerPlayer player, String typeId, String poolId, Map<String, Object> payload, long units) {
        this.player = player; this.typeId = typeId; this.poolId = poolId; this.payload = payload; this.units = units;
    }
    public PlayerAPI getSkills() { return AtlasAPI.player(player); }
    public String getLanguage() { return player.getLanguage(); }
}
