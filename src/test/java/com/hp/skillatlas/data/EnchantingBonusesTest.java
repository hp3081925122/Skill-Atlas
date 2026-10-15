package com.hp.skillatlas.data;

import com.hp.skillatlas.server.EnchantingBonuses;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import java.util.Optional;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnchantingBonusesTest {
    static {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final Holder<Enchantment> SHARPNESS = Holder.direct(new Enchantment(Component.literal("Test"),
            new Enchantment.EnchantmentDefinition(HolderSet.direct(), Optional.empty(), 1, 5,
                    new Enchantment.Cost(1, 0), new Enchantment.Cost(1, 0), 1, List.of(EquipmentSlotGroup.MAINHAND)),
            HolderSet.direct(), DataComponentMap.EMPTY));

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
