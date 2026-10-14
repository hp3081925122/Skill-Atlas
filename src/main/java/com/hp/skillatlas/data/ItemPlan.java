package com.hp.skillatlas.data;

import java.util.List;

public final class ItemPlan {
    public interface Matcher {
        boolean test(int cost, int slot);
    }

    private ItemPlan() {
    }

    public static int[] plan(List<ItemCost> costs, int[] counts, boolean[] plain, Matcher matches, int[] got) {
        int[] left = counts.clone();
        boolean ok = true;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < costs.size(); i++) {
                ItemCost c = costs.get(i);
                boolean specific = !c.nbt.isEmpty();
                if (specific != (pass == 0)) {
                    continue;
                }
                int need = c.count;
                int rounds = 1;
                if (!specific) {
                    rounds = 2;
                }
                for (int round = 0; round < rounds && need > 0; round++) {
                    for (int s = 0; s < left.length && need > 0; s++) {
                        if (left[s] <= 0) {
                            continue;
                        }
                        if (!specific && plain[s] != (round == 0)) {
                            continue;
                        }
                        if (!matches.test(i, s)) {
                            continue;
                        }
                        int used = Math.min(need, left[s]);
                        left[s] -= used;
                        need -= used;
                    }
                }
                got[i] = c.count - need;
                if (need > 0) {
                    ok = false;
                }
            }
        }
        if (!ok) {
            return null;
        }
        int[] used = new int[counts.length];
        for (int s = 0; s < counts.length; s++) {
            used[s] = counts[s] - left[s];
        }
        return used;
    }
}
