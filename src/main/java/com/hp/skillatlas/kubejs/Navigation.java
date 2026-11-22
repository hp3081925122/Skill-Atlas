package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.data.*;
import com.hp.skillatlas.server.AtlasServer;
import com.hp.skillatlas.kubejs.network.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class Navigation {
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private Navigation() {}
    public static Outcome validate(AtlasDefinition definition, PlayerProgress progress, int action, String target) {
        if (target == null || target.length() > 64 || action < 0 || action > 1 || !target.isEmpty() && !Json.validId(target)) return Outcome.fail("invalid_argument");
        if (action == 0) return target.isEmpty() || definition.categories.containsKey(target) ? Outcome.ok() : Outcome.fail("unknown_category");
        SkillNode node = definition.nodes.get(target);
        if (node == null) return Outcome.fail("unknown_node");
        return AtlasLogic.visible(definition, node, progress) ? Outcome.ok() : Outcome.fail("node_hidden");
    }
    public static Outcome send(ServerPlayer player, int action, String target) {
        if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        if (AtlasServer.server() == null || !AtlasServer.server().isSameThread() || AtlasServer.server().getPlayerList().getPlayer(player.getUUID()) != player) return Outcome.fail("player_offline");
        Outcome checked = validate(AtlasServer.definition(), AtlasServer.progress(player), action, target);
        if (!checked.ok) return checked;
        long now = Quotes.time();
        if (now - LAST.getOrDefault(player.getUUID(), -100L) < 5) return Outcome.fail("rate_limited");
        LAST.put(player.getUUID(), now); QuoteNetwork.navigate(player, new NavigationPacket(action, target, Hooks.revision()));
        Outcome result = Outcome.ok(); result.detail = Map.of("state", "sent", "action", action == 0 ? "open" : "focus", "target", target); return result;
    }
    public static void forget(ServerPlayer player) { LAST.remove(player.getUUID()); }
    public static void clear() { LAST.clear(); }
}
