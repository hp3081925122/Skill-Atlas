package com.hp.skillatlas.data;

public final class ActivityMath {
    private ActivityMath() {
    }

    public static int boostedEnchantmentLevel(int original, double chance, double roll, int maximum) {
        return original > 0 && original < maximum && roll < Math.min(100, Math.max(0, chance)) / 100.0D
                ? original + 1 : original;
    }

    public static int fishingWaitRoll(int original, int lure) {
        return Math.max(original, lure * 100 + 20);
    }
}
