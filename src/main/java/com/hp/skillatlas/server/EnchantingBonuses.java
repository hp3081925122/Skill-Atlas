package com.hp.skillatlas.server;

import com.hp.skillatlas.data.ActivityMath;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.List;

public final class EnchantingBonuses {
    private EnchantingBonuses() {
    }

    public static List<EnchantmentInstance> apply(List<EnchantmentInstance> original, RandomSource random, double chance, int maximum) {
        if (chance <= 0 || original.isEmpty()) {
            return original;
        }
        List<EnchantmentInstance> result = new ArrayList<>(original.size());
        for (EnchantmentInstance entry : original) {
            int level = ActivityMath.boostedEnchantmentLevel(entry.level(), chance, random.nextDouble(), maximum);
            result.add(level == entry.level() ? entry : new EnchantmentInstance(entry.enchantment(), level));
        }
        return result;
    }
}
