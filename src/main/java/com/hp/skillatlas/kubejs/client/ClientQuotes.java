package com.hp.skillatlas.kubejs.client;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.data.*;
import com.hp.skillatlas.kubejs.*;
import com.hp.skillatlas.kubejs.network.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class ClientQuotes {
    private record Cached(LearningQuote quote, long received) {}
    private static final LinkedHashMap<String, Cached> CACHE = new LinkedHashMap<>();
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static long ticks, lastRequest = -100;
    private static String viewed = "";
    private ClientQuotes() {}
    public static boolean required(String id) { return Quotes.required(ClientAtlas.definition, id); }
    public static void tick() {
        ticks++;
        if (com.hp.skillatlas.client.screen.AtlasScreen.current() == null) { viewed = ""; return; }
        if (!viewed.isEmpty()) {
            Cached cached = CACHE.get(viewed);
            if (cached != null && ticks - cached.received >= 80) { CACHE.remove(viewed); ClientAtlas.version++; }
            request(viewed);
        }
    }
    public static void view(String id) { if (required(id)) { viewed = id; request(id); } }
    private static void request(String id) {
        Cached cached = CACHE.get(id);
        if (cached != null && ticks - cached.received < 60) return;
        if (ticks - lastRequest < 5 || ticks - REQUESTED.getOrDefault(id, -100L) < 10) return;
        lastRequest = ticks; REQUESTED.put(id, ticks);
        if (cached != null) ClientAtlas.version++;
        QuoteNetwork.send(new QuoteRequest(false, id, ""));
    }
    public static LearningQuote get(String id) {
        Cached value = CACHE.get(id);
        if (value == null || REQUESTED.getOrDefault(id, -100L) > value.received || ticks - value.received >= 80 || value.quote.revision() != ClientAtlas.revision || value.quote.level() != ClientAtlas.progress.level(id)) return null;
        return value.quote;
    }
    public static SkillNode node(SkillNode original) {
        LearningQuote quote = get(original.id);
        if (quote == null || quote.cost().entrySet().isEmpty() || quote.level() >= original.maxLevel()) return original;
        SkillNode copy = original.copy(); copy.levels.set(quote.level(), SkillLevel.fromJson(quote.cost())); return copy;
    }
    public static void receive(LearningQuote quote) {
        if (quote.revision() != ClientAtlas.revision || quote.level() != ClientAtlas.progress.level(quote.nodeId())) return;
        CACHE.remove(quote.nodeId()); CACHE.put(quote.nodeId(), new Cached(quote, ticks));
        while (CACHE.size() > 128) CACHE.remove(CACHE.keySet().iterator().next());
        ClientAtlas.version++;
    }
    public static boolean learn(String id) {
        if (!required(id)) return false;
        LearningQuote quote = get(id);
        if (quote != null && quote.allowed()) {
            CACHE.remove(id); QuoteNetwork.send(new QuoteRequest(true, id, quote.token())); ClientAtlas.version++;
        } else view(id);
        return true;
    }
    public static AtlasLogic.Check check(SkillNode node, AtlasLogic.Check nativeCheck) {
        if (node == null || !required(node.id) || nativeCheck == AtlasLogic.Check.MAXED) return nativeCheck;
        LearningQuote quote = get(node.id);
        if (quote == null) return nativeCheck;
        if (!quote.allowed()) return switch (quote.code()) {
            case "insufficient_points" -> AtlasLogic.Check.NO_POINTS;
            case "insufficient_experience" -> AtlasLogic.Check.NO_EXPERIENCE;
            case "insufficient_items" -> AtlasLogic.Check.NO_ITEMS;
            case "choice_excluded" -> AtlasLogic.Check.EXCLUDED;
            default -> AtlasLogic.Check.LOCKED;
        };
        return nativeCheck;
    }
    public static List<Component> pending(SkillNode node) {
        List<Component> lines = new ArrayList<>(); lines.add(Names.node(node)); lines.add(Component.translatable("skill_atlas_kubejs.quote_pending").withStyle(s -> s.withColor(0x8A93A8)));
        lines.add(Component.empty()); lines.addAll(Names.tooltipLines(node.tooltip)); lines.add(Component.empty()); lines.add(Component.translatable("skill_atlas_kubejs.quote_pending"));
        return lines;
    }
    public static List<Component> append(List<Component> original, LearningQuote quote) {
        List<Component> lines = new ArrayList<>(original);
        if (!quote.nextRewards().isEmpty()) {
            String empty = Component.literal(" • ").append(Component.translatable("skill_atlas.ui.no_rewards")).getString();
            lines.removeIf(c -> c.getString().equals(empty));
        }
        if (!quote.conditions().isEmpty()) {
            lines.add(Component.empty()); lines.add(Component.translatable("skill_atlas_kubejs.conditions").withStyle(s -> s.withColor(0xECC873)));
            for (var condition : quote.conditions()) lines.add(Component.literal(condition.met() ? " ✔ " : " ✘ ").withStyle(s -> s.withColor(condition.met() ? 0x8EDB7A : 0xE0685A)).append(condition.description()));
        }
        addRewards(lines, quote.currentRewards(), "skill_atlas_kubejs.current_rewards");
        addRewards(lines, quote.nextRewards(), "skill_atlas_kubejs.next_rewards");
        if (!quote.allowed() && !quote.code().equals("max_level")) { lines.add(Component.empty()); lines.add(quote.message().copy().withStyle(s -> s.withColor(0xE0685A))); }
        return lines;
    }
    private static void addRewards(List<Component> lines, List<Component> rewards, String key) {
        if (rewards.isEmpty()) return;
        lines.add(Component.empty()); lines.add(Component.translatable(key).withStyle(s -> s.withColor(0xECC873)));
        rewards.forEach(c -> lines.add(Component.literal(" • ").append(c)));
    }
    public static void invalidate() { CACHE.clear(); REQUESTED.clear(); ClientAtlas.version++; }
    public static void clear() { invalidate(); viewed = ""; ticks = 0; lastRequest = -100; }
}
