package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.data.*;
import com.hp.skillatlas.server.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class PlayerAPI {
    private final ServerPlayer player;
    PlayerAPI(ServerPlayer player) { this.player = player; }
    private void thread() {
        if (AtlasServer.server() == null || !AtlasServer.server().isSameThread() || player.level().getServer() != AtlasServer.server()) throw new IllegalStateException("Skill Atlas API requires this server's main thread");
    }
    private SkillNode node(String id) {
        thread(); SkillNode n = AtlasServer.definition().nodes.get(id);
        if (n == null) throw new IllegalArgumentException("Unknown node " + id);
        return n;
    }
    private PointPool pool(String id) {
        thread(); PointPool p = AtlasServer.definition().pools.get(id);
        if (p == null) throw new IllegalArgumentException("Unknown pool " + id);
        return p;
    }
    public int getLevel(String id) { node(id); return AtlasServer.progress(player).level(id); }
    public boolean hasSkill(String id) { return hasSkill(id, 1); }
    public boolean hasSkill(String id, Number level) { return getLevel(id) >= Values.integer(level, 1, 100); }
    public boolean isMaxed(String id) { return getLevel(id) >= node(id).maxLevel(); }
    public Map<String, Integer> getLearnedSkills() { thread(); return Map.copyOf(AtlasServer.progress(player).levels()); }
    public long getPoints(String id) { pool(id); return AtlasServer.progress(player).points(id); }
    public Map<String, Object> getProgress(String id) {
        PointPool pool = pool(id); PlayerProgress p = AtlasServer.progress(player);
        return Map.of("unitsText", Long.toString(p.counter(id)), "targetText", Long.toString(pool.counterTarget()), "awardedText", Long.toString(p.awarded(id)), "limit", pool.limit, "unit", Hooks.unit(pool));
    }
    public long getRawExperience() { thread(); return ExperienceCosts.available(player); }
    public Outcome checkLearn(String id) { thread(); return Hooks.check(player, id); }
    public boolean canLearn(String id) { return checkLearn(id).ok; }
    public Outcome learn(String id) {
        thread(); if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        return Quotes.execute(player, id, Quotes.create(player, id, "learn"));
    }
    public Map<String, Object> getLearningQuote(String id) { thread(); return Quotes.create(player, id, "check").view().snapshot(); }
    public Outcome addPoints(String id, Number amount, String reason) { return change(id, amount, reason, 1); }
    public Outcome spendPoints(String id, Number amount, String reason) { return change(id, amount, reason, -1); }
    public Outcome setPoints(String id, Number amount, String reason) { return change(id, amount, reason, 0); }
    private Outcome change(String id, Number amount, String reason, int mode) {
        pool(id); long value = Values.integer(amount, 0, PlayerProgress.MAX_POINTS);
        if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        long current = getPoints(id);
        if (mode < 0 && current < value) return Outcome.fail("insufficient_points");
        return Hooks.points(player, id, mode == 0 ? value : current + value * mode, reason);
    }
    public Outcome grant(String id, Number level) {
        SkillNode n = node(id); if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        Hooks.clearOutcome(); AtlasServer.grant(player, n, (int)Values.integer(level, 0, n.maxLevel())); return Hooks.lastOutcome();
    }
    public Outcome reset(Map<?, ?> options) {
        thread(); if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        Object scope = options.get("scope"); List<String> ids;
        if ("all".equals(scope)) ids = AtlasAPI.getNodeIds();
        else if ("category".equals(scope)) {
            String id = String.valueOf(options.get("id"));
            if (!AtlasServer.definition().categories.containsKey(id)) return Outcome.fail("unknown_category");
            ids = AtlasAPI.getNodeIds(id);
        } else if ("node".equals(scope)) {
            String id = String.valueOf(options.get("id")); node(id); ids = List.of(id);
        } else return Outcome.fail("invalid_argument");
        Hooks.clearOutcome(); AtlasServer.reset(player, ids); return Hooks.lastOutcome();
    }
    public Outcome addProgress(String id, Number amount, String reason) {
        PointPool pool = pool(id); long units = Values.integer(amount, 0, 1000000000);
        if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        return Hooks.progress(player, pool, units, "script", reason);
    }
    public Outcome emitAcquisition(String type, Map<?, ?> payload, Number amount) { thread(); return Acquisitions.emit(player, type, payload, Values.integer(amount, 0, 1000000000)); }
    public Outcome resetAcquisition(String id) { pool(id); return Hooks.resetAcquisition(player, List.of(id)); }
    public Outcome resetAcquisition() { thread(); return Hooks.resetAcquisition(player, AtlasAPI.getPoolIds()); }
    public Outcome open() { return open(""); }
    public Outcome open(String category) { thread(); return Navigation.send(player, 0, category); }
    public Outcome focus(String node) { thread(); return Navigation.send(player, 1, node); }
}
