package com.hp.skillatlas.kubejs;

import net.minecraft.server.level.ServerPlayer;

public final class ConditionContext {
    public final ServerPlayer player;
    public final String nodeId;
    public final int level;
    public final String phase;
    public ConditionContext(ServerPlayer player, String nodeId, int level, String phase) {
        this.player = player; this.nodeId = nodeId; this.level = level; this.phase = phase;
    }
    public PlayerAPI getSkills() { return AtlasAPI.player(player); }
    public String getLanguage() { return player.getLanguage(); }
}
