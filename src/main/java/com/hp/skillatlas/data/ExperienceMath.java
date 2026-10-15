package com.hp.skillatlas.data;

public final class ExperienceMath {
    private ExperienceMath() {
    }

    public static long atLevel(int level) {
        double n = Math.max(0, level);
        double total;
        if (level <= 16) {
            total = n * n + 6 * n;
        } else if (level <= 31) {
            total = (5 * n * n - 81 * n + 720) / 2;
        } else {
            total = (9 * n * n - 325 * n + 4440) / 2;
        }
        return (long) Math.min(Long.MAX_VALUE, total);
    }

    public static long available(int level, float progress, int nextLevelCost) {
        long base = atLevel(level);
        long partial = Math.max(0, Math.round((double) progress * nextLevelCost));
        return base > Long.MAX_VALUE - partial ? Long.MAX_VALUE : base + partial;
    }

    public static int levelFor(long points) {
        int low = 0;
        int high = Integer.MAX_VALUE;
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            if (atLevel(mid) <= points) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    public static long percentageRefund(long spent, double percent) {
        if (spent <= 0 || !Double.isFinite(percent)) {
            return 0;
        }
        return Math.min(spent, (long) Math.floor(spent * (Math.max(0, Math.min(100, percent)) / 100.0D)));
    }
}
