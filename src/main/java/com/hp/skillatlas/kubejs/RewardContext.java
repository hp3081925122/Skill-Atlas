package com.hp.skillatlas.kubejs;

import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class RewardContext {
    public final ServerPlayer player;
    public final String nodeId;
    public final int level;
    public final int index;
    public final String reason;
    public final String key;
    public final UUID modifierId;
    public final net.minecraft.resources.ResourceLocation modifierKey;
    public RewardContext(ServerPlayer player, String nodeId, int level, int index, String reason) {
        this.player = player; this.nodeId = nodeId; this.level = level; this.index = index; this.reason = reason;
        key = "skill_atlas_kubejs:" + nodeId + "/" + level + "/" + index;
        modifierId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        modifierKey = net.minecraft.resources.ResourceLocation.tryParse(key);
    }
    public PlayerAPI getSkills() { return AtlasAPI.player(player); }
    public String getLanguage() { return player.getLanguage(); }
}
