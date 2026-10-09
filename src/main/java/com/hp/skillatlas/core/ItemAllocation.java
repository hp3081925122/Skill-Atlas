package com.hp.skillatlas.core;

import com.hp.skillatlas.model.Definition;
import java.util.*;
import java.util.function.BiPredicate;

public final class ItemAllocation {
    private ItemAllocation() {}
    public static int[] plan(List<Definition.ItemCost> requirements, int[] counts, BiPredicate<Definition.ItemCost, Integer> matches) {
        int[] remaining = counts.clone();
        List<Definition.ItemCost> ordered = new ArrayList<>(requirements);
        ordered.sort(Comparator.comparing(r -> r.nbt.isBlank()));
        for (var requirement : ordered) {
            int needed = requirement.count;
            for (int slot = 0; slot < remaining.length && needed > 0; slot++) {
                if (remaining[slot] <= 0 || !matches.test(requirement, slot)) continue;
                int used = Math.min(needed, remaining[slot]); remaining[slot] -= used; needed -= used;
            }
            if (needed > 0) return null;
        }
        int[] consumed = new int[counts.length];
        for (int slot = 0; slot < counts.length; slot++) consumed[slot] = counts[slot] - remaining[slot];
        return consumed;
    }
}
