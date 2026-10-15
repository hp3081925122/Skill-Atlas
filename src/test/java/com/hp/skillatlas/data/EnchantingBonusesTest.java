package com.hp.skillatlas.data;

import com.hp.skillatlas.server.EnchantingBonuses;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnchantingBonusesTest {
    private static final Enchantment SHARPNESS = new DamageEnchantment(Enchantment.Rarity.COMMON, 0, EquipmentSlot.MAINHAND);

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
        assertEquals(6, result.get(0).level);
        assertEquals(3, result.get(1).level);
        assertEquals(5, original.get(0).level);
        assertSame(SHARPNESS, result.get(0).enchantment);
        assertSame(original.get(1), result.get(1));
    }

    @Test
    void previewAndActualResultsAgreeForTheSameSeed() {
        List<EnchantmentInstance> original = List.of(new EnchantmentInstance(SHARPNESS, 5), new EnchantmentInstance(SHARPNESS, 3));
        for (int seed = 0; seed < 200; seed++) {
            List<EnchantmentInstance> preview = EnchantingBonuses.apply(original, RandomSource.create(seed), 30, 127);
            List<EnchantmentInstance> actual = EnchantingBonuses.apply(original, RandomSource.create(seed), 30, 127);
            assertEquals(preview.stream().map(entry -> entry.level).toList(), actual.stream().map(entry -> entry.level).toList());
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
