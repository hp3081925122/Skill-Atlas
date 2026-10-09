package com.hp.skillatlas.core;

import com.hp.skillatlas.model.*;
import java.util.*;
import java.math.BigInteger;
import java.util.function.Predicate;

public final class AtlasLogic {
    public static final long LIMIT = 1_000_000_000_000L;
    private AtlasLogic() {}
    public static boolean ready(Definition.Node n, PlayerProgress p) {
        if (n.prerequisites.isEmpty()) return true;
        return "any".equals(n.prerequisiteMode)
            ? n.prerequisites.stream().anyMatch(r -> p.level(r.node) >= r.level)
            : n.prerequisites.stream().allMatch(r -> p.level(r.node) >= r.level);
    }
    public static boolean visible(Definition.Node n, PlayerProgress p, boolean editor) {
        return editor || !n.hideUntilReady || ready(n, p);
    }
    public static String learn(Definition d, PlayerProgress p, String id, int expectedLevel) {
        return learn(d, p, id, expectedLevel, List::isEmpty);
    }
    public static String learn(Definition d, PlayerProgress p, String id, int expectedLevel, Predicate<List<Definition.ItemCost>> deliver) {
        synchronized (p) { return purchase(d, p, id, expectedLevel, deliver); }
    }
    public static String pointPool(Definition d, Definition.Node n, int index) {
        String pool = n.levels.get(index).pool;
        return pool.isEmpty() ? d.categories.get(n.category).pool : pool;
    }
    private static String purchase(Definition d, PlayerProgress p, String id, int expectedLevel, Predicate<List<Definition.ItemCost>> deliver) {
        Definition.Node n = d.nodes.get(id);
        if (n == null) return "unknown_node";
        int current = p.level(id);
        if (current != expectedLevel) return "stale";
        if (current >= n.levels.size()) return "max_level";
        if (!ready(n, p)) return "prerequisite";
        String pool = pointPool(d, n, current);
        long cost = n.levels.get(current).cost;
        PlayerProgress.Balance b = p.balance(pool);
        if (b.points < cost) return "insufficient";
        if (!deliver.test(n.levels.get(current).items)) return "insufficient_items";
        b.points -= cost;
        PlayerProgress.Learned s = p.learned.computeIfAbsent(id, k -> new PlayerProgress.Learned());
        s.payments.add(new PlayerProgress.Payment(pool, cost)); s.level++;
        p.revision = d.revision;
        return "ok";
    }
    private static void reduce(PlayerProgress p, String id, int target) {
        PlayerProgress.Learned s = p.learned.get(id);
        if (s == null) return;
        while (s.payments.size() > target) {
            PlayerProgress.Payment payment = s.payments.remove(s.payments.size() - 1);
            PlayerProgress.Balance b = p.balance(payment.pool);
            b.points = add(b.points, payment.amount);
        }
        s.level = target;
        if (target == 0) p.learned.remove(id);
    }
    public static void reconcile(Definition d, PlayerProgress p) {
        for (String id : new ArrayList<>(p.learned.keySet())) {
            Definition.Node n = d.nodes.get(id);
            reduce(p, id, n == null ? 0 : Math.min(p.level(id), n.levels.size()));
        }
        boolean changed;
        do {
            changed = false;
            for (String id : new ArrayList<>(p.learned.keySet())) {
                if (!ready(d.nodes.get(id), p)) { reduce(p, id, 0); changed = true; }
            }
        } while (changed);
        p.counters.keySet().retainAll(d.rules.keySet());
        p.revision = d.revision;
    }
    public static void reset(Definition d, PlayerProgress p, String id) {
        if ("*".equals(id)) for (String key : new ArrayList<>(p.learned.keySet())) reduce(p, key, 0);
        else reduce(p, id, 0);
        reconcile(d, p);
    }
    public static long add(long a, long b) { return Math.min(LIMIT, a > LIMIT - b ? LIMIT : a + b); }
    public static long requirement(Definition.Pool pool, int level) {
        return Math.min(LIMIT, pool.baseExperience + pool.experienceGrowth * (long)level);
    }
    public static void experience(Definition.Pool pool, PlayerProgress.Balance b, long amount) {
        b.experience = add(b.experience, amount);
        int low = 0, high = 1_000_000 - b.level;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (totalExperience(pool, b.level, mid).compareTo(BigInteger.valueOf(b.experience)) <= 0) low = mid;
            else high = mid - 1;
        }
        if (low > 0) {
            b.experience -= totalExperience(pool, b.level, low).longValueExact();
            b.level += low;
            b.points = add(b.points, Math.min(LIMIT, pool.pointsPerLevel * low));
        }
    }
    public static void setExperience(Definition.Pool pool, PlayerProgress.Balance b, long amount) {
        PlayerProgress.Balance target = new PlayerProgress.Balance(); experience(pool, target, amount);
        b.level = target.level; b.experience = target.experience;
    }
    private static BigInteger totalExperience(Definition.Pool p, int level, int count) {
        BigInteger n = BigInteger.valueOf(count);
        return n.multiply(BigInteger.valueOf(p.baseExperience)).add(n.multiply(BigInteger.valueOf(2L * level + count - 1))
            .multiply(BigInteger.valueOf(p.experienceGrowth)).divide(BigInteger.TWO));
    }
    public static void trigger(Definition d, PlayerProgress p, String id, Definition.Rule r) {
        PlayerProgress.Balance b = p.balance(r.pool);
        if ("experience".equals(r.mode)) experience(d.pools.get(r.pool), b, r.amount);
        else {
            long count = p.counters.getOrDefault(id, 0L) + 1;
            b.points = add(b.points, Math.min(LIMIT, (count / r.threshold) * r.amount));
            p.counters.put(id, count % r.threshold);
        }
    }
    public static List<String> validate(Definition d) {
        try { return validateContents(d); }
        catch (RuntimeException ex) { return List.of("Malformed definition: " + ex.getMessage()); }
    }
    private static List<String> validateContents(Definition d) {
        List<String> e = new ArrayList<>();
        if (d == null || d.schemaVersion != 1 || d.pools == null || d.categories == null || d.nodes == null || d.lines == null || d.rules == null) {
            return List.of("Invalid schema or null collections");
        }
        if (d.nodes.size() > 10000 || d.rules.size() > 2048 || d.categories.size() > 256 || d.pools.size() > 256 || d.lines.size() > 50000)
            e.add("Definition exceeds supported limits");
        for (var entry : d.pools.entrySet()) {
            var p = entry.getValue();
            if (!id(entry.getKey()) || p == null || p.name == null || p.baseExperience < 1 || p.baseExperience > 1_000_000_000L || p.experienceGrowth < 0 || p.experienceGrowth > 1_000_000 || p.pointsPerLevel < 0 || p.pointsPerLevel > 1_000_000) e.add("Invalid pool: " + entry.getKey());
        }
        for (var entry : d.categories.entrySet()) {
            var c = entry.getValue();
            if (!id(entry.getKey()) || c == null || c.name == null || !d.pools.containsKey(c.pool)) e.add("Invalid category: " + entry.getKey());
        }
        for (var entry : d.nodes.entrySet()) {
            String id = entry.getKey(); var n = entry.getValue();
            if (!id(id) || n == null || n.name == null || n.tooltip == null || n.aliases == null || n.icon == null || n.iconNbt == null || !d.categories.containsKey(n.category)
                || !Double.isFinite(n.x) || !Double.isFinite(n.y) || Math.abs(n.x) > 1e7 || Math.abs(n.y) > 1e7
                || n.levels == null || n.levels.isEmpty() || n.levels.size() > 100 || n.prerequisites == null || n.prerequisites.size() > 1000
                || !Set.of("all", "any").contains(n.prerequisiteMode) || !Set.of("item", "texture").contains(n.iconType)) {
                e.add("Invalid node: " + id); continue;
            }
            for (var r : n.prerequisites) {
                var parent = r == null ? null : d.nodes.get(r.node);
                if (parent == null || parent.levels == null || r.level < 1 || r.level > parent.levels.size()) e.add("Invalid prerequisite: " + id);
            }
            for (var l : n.levels) {
                if (l == null || l.pool == null || (!l.pool.isEmpty() && !d.pools.containsKey(l.pool)) || l.cost < 0 || l.cost > LIMIT
                    || l.items == null || l.items.size() > 128 || l.rewards == null || l.rewards.size() > 128) { e.add("Invalid level: " + id); continue; }
                for (var item : l.items) if (item == null || !id(item.item) || item.count < 1 || item.count > 1_000_000 || item.nbt == null || item.nbt.length() > 8192)
                    e.add("Invalid item cost: " + id);
                for (var r : l.rewards) {
                    if (r == null || !id(r.type) || r.target == null || !Double.isFinite(r.amount) || Math.abs(r.amount) > 1e9
                        || !Set.of("addition", "multiply_base", "multiply_total").contains(r.operation) || r.interval < 1 || r.interval > 72000 || r.duration < 1 || r.duration > 72000
                        || r.amplifier < 0 || r.amplifier > 255 || r.effects == null || r.effects.size() > 256 || r.effects.stream().anyMatch(Objects::isNull)) e.add("Invalid reward: " + id);
                }
            }
        }
        for (var entry : d.rules.entrySet()) {
            var r = entry.getValue();
            if (!id(entry.getKey()) || r == null || r.target == null || !Set.of("kill", "mine").contains(r.event) || !Set.of("count", "experience").contains(r.mode)
                || !d.pools.containsKey(r.pool) || r.threshold < 1 || r.threshold > LIMIT || r.amount < 1 || r.amount > 1_000_000_000L) e.add("Invalid rule: " + entry.getKey());
        }
        for (var l : d.lines) {
            var a = l == null ? null : d.nodes.get(l.from); var b = l == null ? null : d.nodes.get(l.to);
            if (a == null || b == null || !Objects.equals(a.category, b.category) || Objects.equals(l.from, l.to)) e.add("Invalid line");
        }
        if (e.isEmpty()) {
            Map<String, Integer> indegree = new HashMap<>(); Map<String, List<String>> children = new HashMap<>();
            d.nodes.keySet().forEach(k -> indegree.put(k, 0));
            d.nodes.forEach((k, n) -> n.prerequisites.forEach(r -> { indegree.merge(k, 1, Integer::sum); children.computeIfAbsent(r.node, x -> new ArrayList<>()).add(k); }));
            ArrayDeque<String> queue = new ArrayDeque<>(); indegree.forEach((k, v) -> { if (v == 0) queue.add(k); });
            int visited = 0;
            while (!queue.isEmpty()) { String key = queue.remove(); visited++; for (String child : children.getOrDefault(key, List.of())) if (indegree.merge(child, -1, Integer::sum) == 0) queue.add(child); }
            if (visited != d.nodes.size()) e.add("Prerequisite cycle detected");
        }
        return e;
    }
    public static boolean id(String id) { return id != null && id.matches("[a-z0-9_./:-]{1,128}"); }
}
