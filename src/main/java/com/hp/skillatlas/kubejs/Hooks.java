package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.AtlasConfig;
import com.hp.skillatlas.ItemCosts;
import com.hp.skillatlas.data.*;
import com.hp.skillatlas.server.*;
import com.hp.skillatlas.network.ResultPacket;
import dev.latvian.mods.kubejs.event.EventResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.*;

public final class Hooks {
    private static final Logger LOG = LoggerFactory.getLogger("skill_atlas_kubejs");
    private static final Map<PlayerProgress, WeakReference<ServerPlayer>> OWNERS = new WeakHashMap<>();
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Outcome> LAST = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE_RESULT = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, Map<String, Long>> MOVEMENT = new LinkedHashMap<>();
    private static AtlasDefinition base;
    private static boolean managed;
    private static boolean reloadPending;
    private static boolean rebuilding;
    private static long revision;
    private static int ticks;
    private Hooks() {}

    private static final class Frame {
        final ServerPlayer player;
        final PlayerProgress progress;
        final Frame parent;
        final String source;
        final String reason;
        final String id = UUID.randomUUID().toString();
        final Map<String, Long> points;
        final Map<String, Integer> levels;
        Outcome outcome = Outcome.ok();
        SkillNode quotedNode;
        Frame(ServerPlayer player, String source, String reason) {
            this.player = player; this.source = source; this.reason = reason;
            progress = AtlasServer.progress(player);
            points = Map.copyOf(progress.allPoints()); levels = Map.copyOf(progress.levels());
            parent = CURRENT.get(); CURRENT.set(this);
        }
        AtlasEvent event() { return new AtlasEvent(player, source, reason, id); }
    }

    public static boolean busy(ServerPlayer player) {
        if (TypeRegistry.evaluating()) return true;
        for (Frame f = CURRENT.get(); f != null; f = f.parent) if (f.player == player) return true;
        return false;
    }
    public static long revision() { return revision; }
    public static String operation(ServerPlayer player) {
        for (Frame frame = CURRENT.get(); frame != null; frame = frame.parent) if (frame.player == player) return frame.source;
        return "refresh";
    }
    public static void revision(long value) { revision = value; }
    public static boolean managed() { return managed; }
    public static void clearOutcome() { LAST.remove(); }
    public static Outcome lastOutcome() { return LAST.get() == null ? Outcome.fail("operation_failed") : LAST.get(); }
    public static void owner(ServerPlayer player, PlayerProgress progress) { OWNERS.put(progress, new WeakReference<>(player)); }
    public static void requestReload() { reloadPending = true; }

    private static void finish(Frame frame) {
        try {
            Set<String> pools = new LinkedHashSet<>(frame.points.keySet()); pools.addAll(frame.progress.allPoints().keySet());
            for (String pool : pools) {
                long before = frame.points.getOrDefault(pool, 0L), after = frame.progress.points(pool);
                if (before == after) continue;
                AtlasEvent e = frame.event(); e.poolId = pool; e.oldPoints = before; e.newPoints = after; e.actualDelta = after - before; e.delta = e.actualDelta;
                AtlasEvents.after(AtlasEvents.POINTS_CHANGED, e, pool);
            }
            Set<String> nodes = new LinkedHashSet<>(frame.levels.keySet()); nodes.addAll(frame.progress.levels().keySet());
            for (String node : nodes) {
                int before = frame.levels.getOrDefault(node, 0), after = frame.progress.level(node);
                if (before == after) continue;
                AtlasEvent e = frame.event(); e.nodeId = node; e.oldLevel = before; e.newLevel = after;
                AtlasEvents.after(AtlasEvents.LEVEL_CHANGED, e, node);
            }
        } finally {
            frame.outcome.transactionId = frame.id;
            Quotes.invalidate(frame.player);
            CURRENT.set(frame.parent); LAST.set(frame.outcome);
        }
    }

    public static Outcome check(ServerPlayer player, String id) {
        var quote = Quotes.create(player, id, "check");
        Outcome result = quote.view().allowed() ? Outcome.ok() : Outcome.fail(quote.view().code(), quote.view().message());
        result.oldLevel = quote.view().level(); result.newLevel = quote.node() == null ? result.oldLevel : Math.min(quote.node().maxLevel(), result.oldLevel + 1);
        result.detail = Values.snapshot(quote.view().cost());
        return result;
    }

    public static Outcome nativeCheck(ServerPlayer player, SkillNode node) {
        AtlasDefinition d = AtlasServer.definition();
        AtlasLogic.Check check = AtlasLogic.check(d, node, AtlasServer.progress(player), items -> ItemCosts.has(player.getInventory(), items), ExperienceCosts.available(player));
        if (check == AtlasLogic.Check.OK && !AtlasLogic.visible(d, node, AtlasServer.progress(player))) check = AtlasLogic.Check.LOCKED;
        Outcome result = check == AtlasLogic.Check.OK ? Outcome.ok() : Outcome.fail(switch (check) {
            case MISSING -> "unknown_node"; case MAXED -> "max_level"; case LOCKED -> "prerequisite"; case EXCLUDED -> "choice_excluded";
            case NO_POINTS -> "insufficient_points"; case NO_EXPERIENCE -> "insufficient_experience"; case NO_ITEMS -> "insufficient_items"; default -> "operation_failed";
        });
        if (node != null) {
            result.oldLevel = AtlasServer.progress(player).level(node.id); result.newLevel = Math.min(node.maxLevel(), result.oldLevel + 1);
            if (result.oldLevel < node.maxLevel()) result.detail = Values.snapshot(node.levels.get(result.oldLevel).toJson());
        }
        return result;
    }

    public static void learn(ServerPlayer player, String id, Runnable original) {
        if (busy(player)) {
            IGNORE_RESULT.set(true);
            try { reject(player, Component.translatable("skill_atlas_kubejs.reentrant_operation")); }
            finally { IGNORE_RESULT.remove(); }
            return;
        }
        Frame frame = new Frame(player, "learn", "learn");
        try {
            Quotes.Prepared prepared = Quotes.provided();
            if (prepared == null && Quotes.required(AtlasServer.definition(), id)) {
                frame.outcome = Outcome.fail("quote_required"); reject(player, frame.outcome.message); return;
            }
            if (prepared == null) prepared = Quotes.create(player, id, "learn");
            if (!prepared.view().nodeId().equals(id) || prepared.view().revision() != revision || prepared.view().level() != frame.progress.level(id) || prepared.view().expires() < Quotes.time()) {
                frame.outcome = Outcome.fail("quote_expired"); reject(player, frame.outcome.message); return;
            }
            frame.quotedNode = prepared.node();
            if (!prepared.view().allowed()) {
                frame.outcome = Outcome.fail(prepared.view().code(), prepared.view().message());
                reject(player, frame.outcome.message);
                AtlasEvent failed = frame.event(); failed.nodeId = id; failed.detail = frame.outcome.code;
                AtlasEvents.after(AtlasEvents.LEARN_FAILED, failed, id); return;
            }
            Outcome check = nativeCheck(player, frame.quotedNode);
            if (!check.ok) frame.outcome = check;
            if (check.ok) {
                AtlasEvent event = frame.event(); event.nodeId = id; event.oldLevel = check.oldLevel; event.newLevel = check.newLevel; event.cost = check.detail;
                event.categoryId = AtlasServer.definition().nodes.get(id).category;
                if (AtlasEvents.denied(AtlasEvents.BEFORE_LEARN, event, id)) {
                    frame.outcome = Outcome.denied(event.denial); reject(player, event.denial); return;
                }
                try {
                    for (var condition : Quotes.conditions(player, id, check.newLevel, "commit")) {
                        if (!condition.met()) { frame.outcome = Outcome.fail("condition_failed", condition.description()); reject(player, condition.description()); return; }
                    }
                } catch (Throwable error) {
                    LOG.error("Skill Atlas final condition check failed for player={} node={}", player.getUUID(), id, error);
                    frame.outcome = Outcome.fail("script_error"); reject(player, frame.outcome.message); return;
                }
            }
            original.run();
            int before = frame.levels.getOrDefault(id, 0), after = frame.progress.level(id);
            frame.outcome.oldLevel = before; frame.outcome.newLevel = after;
            AtlasEvent event = frame.event(); event.nodeId = id; event.oldLevel = before; event.newLevel = after;
            if (after == before + 1 && frame.outcome.ok) { CustomRewards.learned(player, id, before, after); AtlasEvents.after(AtlasEvents.LEARNED, event, id); }
            else AtlasEvents.after(AtlasEvents.LEARN_FAILED, event, id);
        } finally { finish(frame); }
    }

    public static Object quotedNode(Object original) {
        Frame frame = CURRENT.get();
        if (frame != null && frame.quotedNode != null && original instanceof SkillNode node && node.id.equals(frame.quotedNode.id)) return frame.quotedNode;
        return original;
    }

    private static void reject(ServerPlayer player, Component message) {
        AtlasServer.result(player, ResultPacket.LEARN, false, "skill_atlas_kubejs.denied", List.of(message.getString()));
        AtlasServer.sendProgress(player);
    }

    public static void result(ServerPlayer player, int request, boolean ok, String key, List<String> args) {
        Frame frame = CURRENT.get();
        if (!IGNORE_RESULT.get() && frame != null && frame.player == player && request == ResultPacket.LEARN && !ok && frame.outcome.ok) {
            frame.outcome = Outcome.fail(switch (key) {
                case "skill_atlas.learn.no_points" -> "insufficient_points";
                case "skill_atlas.learn.no_experience" -> "insufficient_experience";
                case "skill_atlas.learn.no_items" -> "insufficient_items";
                case "skill_atlas.learn.locked" -> "prerequisite";
                case "skill_atlas.learn.maxed" -> "max_level";
                case "skill_atlas.learn.missing" -> "unknown_node";
                default -> "operation_failed";
            }); frame.outcome.detail = key;
        }
    }

    public static int grant(ServerPlayer player, SkillNode node, Supplier<Integer> original) {
        if (busy(player)) {
            player.sendSystemMessage(Component.translatable("skill_atlas_kubejs.reentrant_operation"));
            return AtlasServer.progress(player).level(node.id);
        }
        Frame frame = new Frame(player, "command", "grant");
        try {
            int level = original.get(); frame.outcome.oldLevel = frame.levels.getOrDefault(node.id, 0); frame.outcome.newLevel = level;
            CustomRewards.learned(player, node.id, frame.outcome.oldLevel, level);
            return level;
        } finally { finish(frame); }
    }

    public static Map<String, Long> reset(ServerPlayer player, Collection<String> nodes, Supplier<Map<String, Long>> original) {
        if (busy(player)) { LAST.set(Outcome.fail("reentrant_operation")); return Map.of(); }
        Frame frame = new Frame(player, "reset", "reset");
        try {
            AtlasEvent event = frame.event(); event.affectedNodes = List.copyOf(nodes);
            String target = nodes.size() == 1 ? nodes.iterator().next() : "";
            event.nodeId = target;
            Map<String, Long> preview = new LinkedHashMap<>();
            nodes.forEach(id -> frame.progress.payments(id).forEach(p -> {
                if (!p.pool().isEmpty()) preview.merge(p.pool(), p.amount(), Long::sum);
            }));
            event.refund = Map.copyOf(preview);
            if (AtlasEvents.denied(AtlasEvents.BEFORE_RESET, event, target)) {
                frame.outcome = Outcome.denied(event.denial); player.sendSystemMessage(event.denial); return Map.of();
            }
            Map<String, Long> refunds = original.get(); frame.outcome.detail = Map.copyOf(refunds);
            event.refund = Map.copyOf(refunds); AtlasEvents.after(AtlasEvents.RESET, event, target); return refunds;
        } finally { finish(frame); }
    }

    private static Long proposal(Frame frame, String pool, long current, long target) {
        long requested = target - current;
        AtlasEvent event = frame.event(); event.poolId = pool; event.oldPoints = current; event.newPoints = target; event.requestedDelta = requested; event.delta = requested;
        if (AtlasEvents.denied(AtlasEvents.POINTS_CHANGING, event, pool)) { frame.outcome = Outcome.denied(event.denial); return null; }
        long delta;
        try { delta = Values.integer(event.delta, -PlayerProgress.MAX_POINTS, PlayerProgress.MAX_POINTS); }
        catch (RuntimeException e) { frame.outcome = Outcome.fail("invalid_argument"); return null; }
        if ((requested > 0 && delta < 0) || (requested < 0 && delta > 0) || (requested == 0 && delta != 0)) { frame.outcome = Outcome.fail("invalid_argument"); return null; }
        if (current + delta < 0) { frame.outcome = Outcome.fail("insufficient_points"); return null; }
        return Math.min(PlayerProgress.MAX_POINTS, current + delta);
    }

    public static Outcome points(ServerPlayer player, String pool, long target, String reason) {
        Frame frame = new Frame(player, "script", Objects.requireNonNull(reason));
        try {
            long current = frame.progress.points(pool); Long value = proposal(frame, pool, current, target);
            if (value != null) {
                frame.progress.setPoints(pool, value); frame.outcome.actualDelta = value - current;
                AtlasServer.data().setDirty(); AtlasServer.sendProgress(player);
            }
        } finally { finish(frame); }
        return frame.outcome;
    }

    public static void balance(PlayerProgress progress, String pool, long target, LongConsumer original) {
        for (Frame current = CURRENT.get(); current != null; current = current.parent) {
            if (current.progress == progress) { original.accept(target); return; }
        }
        WeakReference<ServerPlayer> ref = OWNERS.get(progress); ServerPlayer player = ref == null ? null : ref.get();
        if (player == null || AtlasServer.server() == null || !AtlasServer.server().isSameThread()) { original.accept(target); return; }
        if (busy(player)) throw new IllegalStateException("Reentrant Skill Atlas balance operation");
        Frame frame = new Frame(player, "command", "balance");
        try {
            Long value = proposal(frame, pool, progress.points(pool), Math.max(0, Math.min(PlayerProgress.MAX_POINTS, target)));
            if (value != null) original.accept(value);
        } finally { finish(frame); }
    }

    public static String unit(PointPool pool) {
        var custom = Acquisitions.binding(AtlasServer.definition(), pool.id);
        if (custom != null) return custom.get("unit").getAsString();
        if (pool.method.key.equals("movement")) return "centimeters";
        if (pool.method.key.equals("playtime")) return "seconds";
        return "count";
    }

    public static void naturalProgress(ServerPlayer player, PointPool pool, long amount) {
        if (Acquisitions.binding(AtlasServer.definition(), pool.id) != null) return;
        if (pool.method.key.equals("movement")) {
            MOVEMENT.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>()).merge(pool.id, amount, Math::addExact);
        } else progress(player, pool, amount, "acquire", pool.method.key);
    }

    public static Outcome progress(ServerPlayer player, PointPool pool, long amount, String source, String reason) {
        return progress(player, pool, amount, source, reason, Map.of());
    }
    public static Outcome progress(ServerPlayer player, PointPool pool, long amount, String source, String reason, Map<String, Object> payload) {
        if (busy(player)) return Outcome.fail("reentrant_operation");
        Frame frame = new Frame(player, source, reason);
        try {
            PlayerProgress p = frame.progress;
            if (pool.limit > 0 && p.awarded(pool.id) >= pool.limit) return frame.outcome;
            var custom = Acquisitions.binding(AtlasServer.definition(), pool.id);
            AtlasEvent event = frame.event(); event.poolId = pool.id; event.acquisitionType = custom == null ? pool.method.key : custom.get("type").getAsString(); event.unit = unit(pool); event.units = amount; event.detail = payload;
            if (AtlasEvents.denied(AtlasEvents.PROGRESSING, event, pool.id)) { frame.outcome = Outcome.denied(event.denial); return frame.outcome; }
            long units = Values.integer(event.units, 0, 9007199254740991L);
            long total = Math.addExact(p.counter(pool.id), units), target = pool.counterTarget();
            long gain = Math.multiplyExact(total / target, pool.points);
            if (pool.limit > 0) gain = Math.min(gain, pool.limit - p.awarded(pool.id));
            long current = p.points(pool.id), actual = 0;
            if (gain > 0) {
                Long proposed = proposal(frame, pool.id, current, Math.min(PlayerProgress.MAX_POINTS, Math.addExact(current, gain)));
                if (proposed != null) {
                    actual = proposed - current;
                    if (pool.limit > 0) actual = Math.min(actual, pool.limit - p.awarded(pool.id));
                    p.setPoints(pool.id, current + actual); p.addAwarded(pool.id, actual);
                }
            }
            p.setCounter(pool.id, gain > 0 && actual == 0 ? total : total % target);
            frame.outcome.actualDelta = actual;
            AtlasServer.data().setDirty(); AtlasServer.markSync(player);
            if (actual > 0 && AtlasConfig.NOTIFY_POINTS.get()) {
                Component name = Texts.component(AtlasServer.definition().texts.display(pool.name, player.getLanguage()), pool.id).withStyle(s -> s.withColor(pool.color));
                player.sendSystemMessage(Component.translatable("skill_atlas.message.points_gained", actual, name), true);
            }
            return frame.outcome;
        } catch (RuntimeException e) {
            frame.outcome = Outcome.fail("script_error"); LOG.error("Skill Atlas progression failed for player={} pool={}", player.getUUID(), pool.id, e); return frame.outcome;
        } finally { finish(frame); }
    }

    public static AtlasDefinition overlay(AtlasDefinition input) {
        if (rebuilding) throw new IllegalStateException("Recursive atlas definition reload");
        rebuilding = true;
        DefinitionsEvent event = new DefinitionsEvent(input);
        try {
            TypeRegistry types = TypeRegistry.build();
            if (AtlasEvents.DEFINITIONS.hasListeners()) {
                EventResult result = AtlasEvents.post(AtlasEvents.DEFINITIONS, event, null);
                if (result.type() == dev.latvian.mods.kubejs.event.EventResult.Type.ERROR || result.interruptFalse()) throw new IllegalStateException("SkillAtlasEvents.definitions failed");
            }
            if (AtlasEvents.LEARNING_COST.hasListeners()) ExtraData.get(event.draft()).addProperty("$dynamic_cost", true);
            else ExtraData.get(event.draft()).remove("$dynamic_cost");
            AtlasDefinition next = event.finish(types);
            TypeRegistry.commit(types); Quotes.invalidateAll();
            managed = !ExtraData.get(next).entrySet().isEmpty() || !input.toJson().equals(next.toJson()); base = input.copy(); reloadPending = false;
            return next;
        } catch (RuntimeException e) {
            LOG.error("Skill Atlas script definition rejected; previous effective atlas retained", e);
            throw new AtlasException("skill_atlas_kubejs.definition_error", e.toString());
        } finally { event.close(); rebuilding = false; }
    }

    public static AtlasDefinition saveDefinition(AtlasDefinition current) { return managed && base != null ? base : current; }
    public static void definitionChange(Runnable original) {
        List<Frame> frames = new ArrayList<>();
        for (ServerPlayer player : AtlasServer.server().getPlayerList().getPlayers()) {
            if (busy(player)) throw new IllegalStateException("Definition changed during a player transaction");
            frames.add(new Frame(player, "definition_change", "reconcile"));
        }
        try { original.run(); }
        finally {
            Collections.reverse(frames);
            frames.forEach(Hooks::finish);
        }
    }
    public static void login(ServerPlayer player, Runnable original) {
        Frame frame = new Frame(player, "login", "seed");
        try { original.run(); } finally { finish(frame); }
    }
    public static void loaded(boolean ok) {
        Quotes.invalidateAll();
        AtlasEvent event = new AtlasEvent(null, "definition_change", "reload", UUID.randomUUID().toString());
        event.detail = ok ? AtlasAPI.exportDefinition() : AtlasServer.loadError();
        if (ok && AtlasEvents.RELOADED.hasListeners()) AtlasEvents.RELOADED.post(event);
        if (!ok && AtlasEvents.RELOAD_FAILED.hasListeners()) AtlasEvents.RELOAD_FAILED.post(event);
    }

    public static void tick() {
        if (AtlasServer.server() == null) return;
        Quotes.tick();
        if (reloadPending && !rebuilding && CURRENT.get() == null) { reloadPending = false; AtlasServer.reload(); }
        if (++ticks % 20 == 0) flush(null);
    }

    public static void flush(ServerPlayer only) {
        if (AtlasServer.server() == null) return;
        for (UUID id : new ArrayList<>(MOVEMENT.keySet())) {
            if (only != null && !only.getUUID().equals(id)) continue;
            ServerPlayer player = only != null ? only : AtlasServer.server().getPlayerList().getPlayer(id);
            Map<String, Long> pending = MOVEMENT.remove(id);
            if (player == null) continue;
            pending.forEach((poolId, units) -> {
                PointPool pool = AtlasServer.definition().pools.get(poolId);
                if (pool != null) progress(player, pool, units, "acquire", "movement");
            });
        }
    }

    public static void clear() {
        CustomRewards.clear("stop"); Quotes.clear(); TypeRegistry.clear(); Acquisitions.clear(); Navigation.clear();
        OWNERS.clear(); MOVEMENT.clear(); CURRENT.remove(); LAST.remove(); IGNORE_RESULT.remove();
        base = null; managed = false; reloadPending = false; rebuilding = false; ticks = 0;
    }
    public static Outcome resetAcquisition(ServerPlayer player, List<String> ids) {
        if (busy(player)) return Outcome.fail("reentrant_operation");
        for (String id : ids) if (!AtlasServer.definition().pools.containsKey(id)) return Outcome.fail("unknown_pool");
        Frame frame = new Frame(player, "script", "reset_acquisition");
        try {
            List<Map<String, Object>> details = new ArrayList<>(); Map<String, Long> pending = MOVEMENT.get(player.getUUID());
            for (String id : ids) {
                long discarded = pending == null ? 0 : pending.getOrDefault(id, 0L);
                details.add(Map.of("poolId", id, "oldUnitsText", Long.toString(frame.progress.counter(id)), "oldAwardedText", Long.toString(frame.progress.awarded(id)), "discardedPendingText", Long.toString(discarded), "unitsText", "0", "awardedText", "0"));
                frame.progress.setCounter(id, 0); ((com.hp.skillatlas.kubejs.mixin.ProgressAcquisitionAccess)(Object)frame.progress).atlasKjsAwarded().remove(id);
                if (pending != null) pending.remove(id);
            }
            if (pending != null && pending.isEmpty()) MOVEMENT.remove(player.getUUID());
            frame.outcome.detail = List.copyOf(details); AtlasServer.data().setDirty(); AtlasServer.markSync(player);
        } finally { finish(frame); }
        return frame.outcome;
    }
}
