package com.hp.skillatlas.model;

import java.util.*;

public final class PlayerProgress {
    public long revision;
    public Map<String, Balance> pools = new LinkedHashMap<>();
    public Map<String, Learned> learned = new LinkedHashMap<>();
    public Map<String, Long> counters = new LinkedHashMap<>();
    public Balance balance(String id) { return pools.computeIfAbsent(id, k -> new Balance()); }
    public int level(String id) { Learned s = learned.get(id); return s == null ? 0 : s.level; }
    public static final class Balance {
        public long points;
        public long experience;
        public int level;
    }
    public static final class Learned {
        public int level;
        public List<Payment> payments = new ArrayList<>();
    }
    public static final class Payment {
        public String pool;
        public long amount;
        public Payment() {}
        public Payment(String pool, long amount) { this.pool = pool; this.amount = amount; }
    }
}
