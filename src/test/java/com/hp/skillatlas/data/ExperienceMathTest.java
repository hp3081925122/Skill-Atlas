package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceMathTest {
    @Test
    void piecewiseTotalMatchesVanillaLevelCostsAcrossBothBoundaries() {
        long sum = 0;
        for (int level = 0; level <= 1_000; level++) {
            assertEquals(sum, ExperienceMath.atLevel(level), "level " + level);
            int cost = level < 15 ? 7 + level * 2 : level < 30 ? 37 + (level - 15) * 5 : 112 + (level - 30) * 9;
            sum += cost;
        }
    }

    @Test
    void rawPaymentPreservesRemainingProgressRatherThanTakingWholeLevels() {
        long balance = ExperienceMath.available(10, 0.0F, 27);
        assertEquals(160, balance);
        long after = balance - 10;
        int level = ExperienceMath.levelFor(after);
        assertEquals(9, level);
        assertEquals(15, after - ExperienceMath.atLevel(level));
        assertEquals(after, ExperienceMath.available(level, 15 / 25.0F, 25));
    }

    @Test
    void fractionalProgressRestoresIntegerPointsAndLargeBalancesDoNotOverflow() {
        assertEquals(1, ExperienceMath.available(0, 1 / 7.0F, 7));
        assertEquals(6, ExperienceMath.available(0, 6 / 7.0F, 7));
        assertEquals(Long.MAX_VALUE, ExperienceMath.available(Integer.MAX_VALUE, 0.5F, 10));
        assertEquals(0, ExperienceMath.levelFor(0));
        int level = ExperienceMath.levelFor(Integer.MAX_VALUE);
        assertTrue(ExperienceMath.atLevel(level) <= Integer.MAX_VALUE);
        assertTrue(ExperienceMath.atLevel(level + 1) > Integer.MAX_VALUE);
    }

    @Test
    void paymentKeepsLargeAdminBalancesAboveTheVanillaTotalCounterLimit() {
        long balance = ExperienceMath.available(100_000, 0.0F, 899_842);
        assertTrue(balance > Integer.MAX_VALUE);
        assertEquals(99_999, ExperienceMath.levelFor(balance - 10));
        assertEquals(balance, ExperienceMath.atLevel(100_000));
    }
}
