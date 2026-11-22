package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import com.hp.skillatlas.data.*;
import com.hp.skillatlas.server.AtlasServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.function.Supplier;

public final class Quotes {
    public record Prepared(LearningQuote view, SkillNode node) {}
    private static final Map<UUID, LinkedHashMap<String, Prepared>> CACHE = new HashMap<>();
    private static final Map<UUID, Long> REQUESTS = new HashMap<>();
    private static final ThreadLocal<Prepared> PROVIDED = new ThreadLocal<>();
    private static long ticks;
    private Quotes() {}
    public static boolean required(AtlasDefinition definition, String id) { return ExtraData.has(definition, id) || ExtraData.dynamic(definition); }
    public static void tick() { ticks++; }
    public static long time() { return ticks; }
    public static void invalidate(ServerPlayer player) { CACHE.remove(player.getUUID()); }
    public static void invalidateAll() { CACHE.clear(); }
    public static void forget(ServerPlayer player) { invalidate(player); REQUESTS.remove(player.getUUID()); }
    public static void clear() { CACHE.clear(); REQUESTS.clear(); PROVIDED.remove(); ticks = 0; }
    public static Prepared provided() { return PROVIDED.get(); }
    public static <T> T with(Prepared quote, Supplier<T> action) {
        Prepared old = PROVIDED.get(); PROVIDED.set(quote);
        try { return action.get(); } finally { if (old == null) PROVIDED.remove(); else PROVIDED.set(old); }
    }
    public static Prepared create(ServerPlayer player, String id, String phase) {
        if (TypeRegistry.evaluating()) throw new IllegalStateException("Recursive quote evaluation");
        var definition = AtlasServer.definition(); SkillNode base = definition.nodes.get(id);
        int current = AtlasServer.progress(player).level(id);
        SkillNode node = base == null ? null : base.copy();
        List<LearningQuote.ConditionLine> conditions = List.of(); List<Component> currentRewards = List.of(), nextRewards = List.of();
        String code = "ok"; Component message = Component.empty();
        JsonObject cost = new JsonObject();
        try {
            if (node != null && current < node.maxLevel()) {
                LearningCostEvent event = new LearningCostEvent(player, node, current, phase);
                if (TypeRegistry.evaluate(() -> AtlasEvents.denied(AtlasEvents.LEARNING_COST, event, id))) {
                    code = event.callbackFailed ? "script_error" : "script_denied"; message = event.denial;
                } else {
                    SkillLevel rank = event.freeze(node.levels.get(current), definition.poolOf(node));
                    node.levels.set(current, rank); cost = rank.toJson();
                    if (cost.toString().length() > 1048576) throw new IllegalArgumentException("Dynamic fee data is too large");
                }
                conditions = conditions(player, id, current + 1, phase);
                for (var c : conditions) if (!c.met() && code.equals("ok")) { code = "condition_failed"; message = c.description(); }
                nextRewards = CustomRewards.descriptions(player, id, current + 1);
            }
            if (node != null && current > 0) currentRewards = CustomRewards.descriptions(player, id, Math.min(current, node.maxLevel()));
            if (code.equals("ok")) {
                Outcome check = Hooks.nativeCheck(player, node);
                code = check.code; message = check.message;
            }
        } catch (Throwable error) {
            cost = new JsonObject();
            code = "script_error"; message = Component.translatable("skill_atlas_kubejs.script_error");
            LoggerFactory.getLogger("skill_atlas_kubejs").error("Skill Atlas quote failed for player={} node={}", player.getUUID(), id, error);
        }
        LearningQuote view = new LearningQuote(id, Hooks.revision(), current, UUID.randomUUID().toString(), ticks + 100, code.equals("ok"), code, message, cost, conditions, currentRewards, nextRewards);
        try { com.hp.skillatlas.kubejs.network.QuoteResponse.validate(view); }
        catch (RuntimeException error) {
            LoggerFactory.getLogger("skill_atlas_kubejs").error("Skill Atlas quote exceeded protocol bounds for player={} node={}", player.getUUID(), id, error);
            view = new LearningQuote(id, Hooks.revision(), current, view.token(), ticks + 100, false, "script_error", Component.translatable("skill_atlas_kubejs.script_error"), new JsonObject(), List.of(), List.of(), List.of());
        }
        return new Prepared(view, node);
    }
    public static List<LearningQuote.ConditionLine> conditions(ServerPlayer player, String id, int level, String phase) {
        List<LearningQuote.ConditionLine> result = new ArrayList<>();
        for (JsonObject entry : ExtraData.entries(AtlasServer.definition(), id, level, "conditions", true)) {
            ConditionType type = TypeRegistry.current().conditions.get(entry.get("type").getAsString());
            if (type == null) throw new IllegalStateException("Unknown condition type " + entry.get("type"));
            ConditionContext context = new ConditionContext(player, id, level, phase);
            Map<String, Object> data = TypeRegistry.data(entry.getAsJsonObject("data"));
            result.add(TypeRegistry.evaluate(() -> new LearningQuote.ConditionLine(type.test.test(context, data), Objects.requireNonNull(type.description.apply(context, data)))));
        }
        return List.copyOf(result);
    }
    public static LearningQuote request(ServerPlayer player, String id) {
        if (ticks - REQUESTS.getOrDefault(player.getUUID(), -100L) < 5) return null;
        REQUESTS.put(player.getUUID(), ticks);
        Prepared value = create(player, id, "preview");
        LinkedHashMap<String, Prepared> quotes = CACHE.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>());
        quotes.remove(id); quotes.put(id, value);
        while (quotes.size() > 128) quotes.remove(quotes.keySet().iterator().next());
        return value.view;
    }
    public static Outcome learn(ServerPlayer player, String id, String token) {
        if (Hooks.busy(player)) return Outcome.fail("reentrant_operation");
        Map<String, Prepared> quotes = CACHE.get(player.getUUID());
        Prepared quote = quotes == null ? null : quotes.remove(id);
        if (quote == null || !quote.view.token().equals(token) || quote.view.revision() != Hooks.revision() || quote.view.level() != AtlasServer.progress(player).level(id) || quote.view.expires() < ticks) {
            Outcome failed = Outcome.fail("quote_expired");
            AtlasServer.result(player, com.hp.skillatlas.network.ResultPacket.LEARN, false, "skill_atlas_kubejs.quote_expired", List.of());
            return failed;
        }
        return execute(player, id, quote);
    }
    public static Outcome execute(ServerPlayer player, String id, Prepared quote) {
        return with(quote, () -> { Hooks.clearOutcome(); AtlasServer.learn(player, id); return Hooks.lastOutcome(); });
    }
}
