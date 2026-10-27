package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.hp.skillatlas.server.AtlasServer;
import java.util.*;

public class AtlasEvent extends EventJS {
    public final ServerPlayer player;
    public final MinecraftServer server;
    public final String source;
    public final String reason;
    public final String transactionId;
    public String nodeId = "";
    public String poolId = "";
    public String categoryId = "";
    public String acquisitionType = "";
    public String unit = "count";
    public int oldLevel;
    public int newLevel;
    public long oldPoints;
    public long newPoints;
    public long requestedDelta;
    public long actualDelta;
    public double delta;
    public double units;
    public Object cost;
    public Object affectedNodes;
    public Object refund;
    public Object detail;
    public Component denial = Component.translatable("skill_atlas_kubejs.script_denied");
    boolean callbackFailed;

    public AtlasEvent(ServerPlayer player, String source, String reason, String transactionId) {
        this.player = player;
        this.server = AtlasServer.server();
        this.source = source;
        this.reason = reason;
        this.transactionId = transactionId;
    }

    public Object deny(Component reason) throws dev.latvian.mods.kubejs.event.EventExit {
        this.denial = Objects.requireNonNull(reason);
        return cancel();
    }

    public long getRevision() { return AtlasAPI.getRevision(); }
}
