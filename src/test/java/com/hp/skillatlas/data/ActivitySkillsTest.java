package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActivitySkillsTest {
    @Test
    void defaultsUseSixteenHarvestsAndFourCatches() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        assertEquals(RuleType.HARVEST, d.pools.get("farming").method);
        assertEquals(16, d.pools.get("farming").count);
        assertEquals(1, d.pools.get("farming").points);
        assertEquals(RuleType.FISH, d.pools.get("fishing").method);
        assertEquals(4, d.pools.get("fishing").count);
        assertFalse(d.nodes.containsKey("harvest_experience"));
        assertEquals(7, d.nodesIn("farming").size());
        for (int i = 1; i <= 5; i++) {
            SkillNode node = d.nodes.get("harvest_experience_" + i);
            assertEquals(1, node.maxLevel());
            assertEquals(1, node.levels.get(0).cost);
            assertEquals(1, node.levels.get(0).rewards.get(0).amount);
        }
        assertEquals(3, d.nodes.get("fishing_lure").maxLevel());
        assertEquals(3, d.nodes.get("fishing_luck").maxLevel());
    }

    @Test
    void experienceCategoryNeedsNoPoolAndCannotSpendSkillPoints() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode n = d.nodes.get("enchanting_cap");
        PlayerProgress p = new PlayerProgress();
        p.setPoints("mining", 1_000_000);
        assertTrue(d.categories.get("enchanting").hasNoPool());
        assertFalse(d.pools.containsKey("enchanting"));
        assertEquals(AtlasLogic.Check.NO_EXPERIENCE, AtlasLogic.check(d, n, p, items -> true, 9));
        assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true, 10));
        assertEquals(30, n.maxLevel());
        assertEquals(300, n.levels.get(29).cost);
        assertEquals(30, n.levels.get(29).rewards.get(0).amount);
        assertEquals(4_650, n.levels.stream().mapToInt(level -> level.cost).sum());
    }

    @Test
    void experienceRefundDoesNotCreateAFakePointPoolAndIsConsumedOnce() {
        PlayerProgress p = new PlayerProgress();
        p.recordPayment("enchanting_cap", PlayerProgress.EXPERIENCE, 10);
        p.recordPayment("enchanting_cap", PlayerProgress.EXPERIENCE, 20);
        p.setLevel("enchanting_cap", 2);
        p.truncate("enchanting_cap", 0);
        assertTrue(p.allPoints().isEmpty());
        assertEquals(30, p.takeExperienceRefund());
        assertEquals(0, p.takeExperienceRefund());
    }

    @Test
    void lureCannotProduceANegativeWait() {
        for (int lure = 0; lure <= 6; lure++) {
            for (int roll = 100; roll <= 600; roll++) {
                int wait = ActivityMath.fishingWaitRoll(roll, lure) - lure * 100;
                assertEquals(Math.max(20, roll - lure * 100), wait);
            }
        }
    }
}
