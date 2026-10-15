package com.hp.skillatlas.data;

import com.hp.skillatlas.server.EnchantingBonuses;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnchantingBonusesTest {
    private static final Holder<Enchantment> SHARPNESS = Holder.Reference.createStandAlone(new HolderOwner<>() {}, Enchantments.SHARPNESS);

    @Test
    void eachEntryUsesItsOwnRollAndOriginalEntriesStayUnchanged() {
        long seed = 0;
        while (seed < 10_000) {
            RandomSource probe = RandomSource.create(seed);
            if (probe.nextDouble() < 0.3 && probe.nextDouble() >= 0.3) {
                break;
            }
            seed++;
        }
        assertTrue(seed < 10_000);
        List<EnchantmentInstance> original = List.of(new EnchantmentInstance(SHARPNESS, 5), new EnchantmentInstance(SHARPNESS, 3));
        List<EnchantmentInstance> result = EnchantingBonuses.apply(original, RandomSource.create(seed), 30, 127);
        assertEquals(6, result.get(0).level());
        assertEquals(3, result.get(1).level());
        assertEquals(5, original.get(0).level());
        assertSame(SHARPNESS, result.get(0).enchantment());
        assertSame(original.get(1), result.get(1));
    }

    @Test
    void previewAndActualResultsAgreeForTheSameSeed() {
        List<EnchantmentInstance> original = List.of(new EnchantmentInstance(SHARPNESS, 5), new EnchantmentInstance(SHARPNESS, 3));
        for (int seed = 0; seed < 200; seed++) {
            List<EnchantmentInstance> preview = EnchantingBonuses.apply(original, RandomSource.create(seed), 30, 127);
            List<EnchantmentInstance> actual = EnchantingBonuses.apply(original, RandomSource.create(seed), 30, 127);
            assertEquals(preview.stream().map(EnchantmentInstance::level).toList(), actual.stream().map(EnchantmentInstance::level).toList());
        }
    }

    @Test
    void zeroChanceKeepsVanillaResultsAndDoesNotConsumeRandomness() {
        List<EnchantmentInstance> original = List.of(new EnchantmentInstance(SHARPNESS, 5));
        RandomSource random = RandomSource.create(10);
        assertSame(original, EnchantingBonuses.apply(original, random, 0, 127));
        assertEquals(RandomSource.create(10).nextDouble(), random.nextDouble());
        assertSame(original, EnchantingBonuses.apply(original, random, -10, 127));
    }

    @Test
    void chanceUsesPercentagePointsAndRespectsStorageBounds() {
        int boosted = 0;
        for (int draw = 0; draw < 10_000; draw++) {
            if (ActivityMath.boostedEnchantmentLevel(5, 30, draw / 10_000.0D, 127) == 6) {
                boosted++;
            }
        }
        assertEquals(3_000, boosted);
        assertEquals(5, ActivityMath.boostedEnchantmentLevel(5, 30, 0.3, 127));
        assertEquals(127, ActivityMath.boostedEnchantmentLevel(127, 100, 0, 127));
        assertEquals(128, ActivityMath.boostedEnchantmentLevel(127, 100, 0, 255));
        assertEquals(255, ActivityMath.boostedEnchantmentLevel(255, 100, 0, 255));
        assertEquals(0, ActivityMath.boostedEnchantmentLevel(0, 100, 0, 127));
        assertEquals(6, ActivityMath.boostedEnchantmentLevel(5, 100, 0.99999, 127));
    }
}
