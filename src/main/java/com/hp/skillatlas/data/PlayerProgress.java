package com.hp.skillatlas.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerProgress {
    public static final long MAX_POINTS = 1_000_000_000_000L;

    public record Payment(String pool, long amount) {
    }

    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, Long> points = new HashMap<>();
    private final Map<String, Long> counters = new HashMap<>();
    private final Map<String, Long> awarded = new HashMap<>();
    private final Map<String, List<Payment>> payments = new HashMap<>();
    private final Set<String> seeded = new HashSet<>();

    public int level(String node) {
        return levels.getOrDefault(node, 0);
    }

    public Map<String, Integer> levels() {
        return Collections.unmodifiableMap(levels);
    }

    public void setLevel(String node, int level) {
        if (level <= 0) {
            levels.remove(node);
        } else {
            levels.put(node, level);
        }
    }

    public long points(String pool) {
        return points.getOrDefault(pool, 0L);
    }

    public Map<String, Long> allPoints() {
        return Collections.unmodifiableMap(points);
    }

    public void setPoints(String pool, long value) {
        long v = Math.max(0L, Math.min(MAX_POINTS, value));
        if (v == 0L) {
            points.remove(pool);
        } else {
            points.put(pool, v);
        }
    }

    public void addPoints(String pool, long delta) {
        setPoints(pool, points(pool) + delta);
    }

    public long counter(String pool) {
        return counters.getOrDefault(pool, 0L);
    }

    public void setCounter(String pool, long value) {
        if (value <= 0L) {
            counters.remove(pool);
        } else {
            counters.put(pool, value);
        }
    }

    public long awarded(String pool) {
        return awarded.getOrDefault(pool, 0L);
    }

    public void addAwarded(String pool, long value) {
        awarded.merge(pool, value, Long::sum);
    }

    public List<Payment> payments(String node) {
        return payments.getOrDefault(node, Collections.emptyList());
    }

    public void recordPayment(String node, String pool, long amount) {
        payments.computeIfAbsent(node, k -> new ArrayList<>()).add(new Payment(pool, amount));
    }

    public void alignPayments(String node, int level) {
        List<Payment> list = payments.computeIfAbsent(node, k -> new ArrayList<>());
        while (list.size() < level) {
            list.add(new Payment("", 0L));
        }
    }

    public Map<String, Long> truncate(String node, int level) {
        Map<String, Long> refunds = new HashMap<>();
        List<Payment> list = payments.get(node);
        if (list != null) {
            while (list.size() > Math.max(0, level)) {
                Payment p = list.remove(list.size() - 1);
                if (!p.pool().isEmpty() && p.amount() > 0L) {
                    refunds.merge(p.pool(), p.amount(), Long::sum);
                }
            }
            if (list.isEmpty()) {
                payments.remove(node);
            }
        }
        refunds.forEach(this::addPoints);
        setLevel(node, level);
        return refunds;
    }

    public boolean seed(AtlasDefinition definition) {
        boolean changed = false;
        for (PointPool p : definition.pools.values()) {
            if (seeded.add(p.id)) {
                if (p.initial > 0) {
                    addPoints(p.id, p.initial);
                }
                changed = true;
            }
        }
        return changed;
    }

    public boolean migrate(AtlasDefinition definition, Set<String> only) {
        boolean changed = false;
        List<String> ids = new ArrayList<>(levels.keySet());
        for (String id : ids) {
            if (only != null && !only.contains(id)) {
                continue;
            }
            SkillNode node = definition.nodes.get(id);
            int max = 0;
            if (node != null) {
                max = node.maxLevel();
            }
            if (level(id) > max) {
                truncate(id, max);
                changed = true;
            }
        }
        return changed;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag lv = new CompoundTag();
        levels.forEach(lv::putInt);
        tag.put("levels", lv);
        CompoundTag pt = new CompoundTag();
        points.forEach(pt::putLong);
        tag.put("points", pt);
        CompoundTag ct = new CompoundTag();
        counters.forEach(ct::putLong);
        tag.put("counters", ct);
        CompoundTag aw = new CompoundTag();
        awarded.forEach(aw::putLong);
        tag.put("awarded", aw);
        CompoundTag pay = new CompoundTag();
        payments.forEach((node, list) -> {
            ListTag l = new ListTag();
            for (Payment p : list) {
                CompoundTag e = new CompoundTag();
                e.putString("pool", p.pool());
                e.putLong("amount", p.amount());
                l.add(e);
            }
            pay.put(node, l);
        });
        tag.put("payments", pay);
        ListTag sd = new ListTag();
        for (String s : seeded) {
            sd.add(StringTag.valueOf(s));
        }
        tag.put("seeded", sd);
        return tag;
    }

    public static PlayerProgress load(CompoundTag tag) {
        PlayerProgress p = new PlayerProgress();
        CompoundTag lv = tag.getCompound("levels");
        for (String k : lv.getAllKeys()) {
            p.setLevel(k, lv.getInt(k));
        }
        CompoundTag pt = tag.getCompound("points");
        for (String k : pt.getAllKeys()) {
            p.setPoints(k, pt.getLong(k));
        }
        CompoundTag ct = tag.getCompound("counters");
        for (String k : ct.getAllKeys()) {
            p.setCounter(k, ct.getLong(k));
        }
        CompoundTag aw = tag.getCompound("awarded");
        for (String k : aw.getAllKeys()) {
            p.awarded.put(k, aw.getLong(k));
        }
        CompoundTag pay = tag.getCompound("payments");
        for (String k : pay.getAllKeys()) {
            ListTag l = pay.getList(k, Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag e = l.getCompound(i);
                p.recordPayment(k, e.getString("pool"), e.getLong("amount"));
            }
        }
        ListTag sd = tag.getList("seeded", Tag.TAG_STRING);
        for (int i = 0; i < sd.size(); i++) {
            p.seeded.add(sd.getString(i));
        }
        return p;
    }
}
