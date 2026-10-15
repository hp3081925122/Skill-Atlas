package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnchantingMajorsTest {
    @Test
    void fourMajorNodesUseRawExperienceAndRequireTheExistingEnchantingSkill() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        assertEquals(5, d.nodesIn("enchanting").size());
        assertEquals(30, d.nodes.get("enchanting_cap").maxLevel());
        Map<String, Double> amounts = Map.of("lapis_saving", 30.0, "enchanting_refund", 30.0, "book_copy", 15.0, "enchanting_insight", 1.0);
        PlayerProgress p = new PlayerProgress();
        for (var entry : amounts.entrySet()) {
            SkillNode n = d.nodes.get(entry.getKey());
            assertTrue(n.size > 1.0D);
            assertEquals(1, n.maxLevel());
            assertEquals(AtlasLogic.Check.LOCKED, AtlasLogic.check(d, n, p, items -> true, 100));
            p.setLevel("enchanting_cap", 1);
            assertEquals(AtlasLogic.Check.NO_EXPERIENCE, AtlasLogic.check(d, n, p, items -> true, 99));
            assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true, 100));
            SkillLevel level = n.levels.get(0);
            assertTrue(level.experience);
            assertTrue(level.items.isEmpty());
            assertEquals("skill_atlas:" + entry.getKey(), level.rewards.get(0).target);
            assertEquals(entry.getValue(), level.rewards.get(0).amount);
            p.setLevel("enchanting_cap", 0);
        }
    }

    @Test
    void refundUsesRawPointsSpentRatherThanTheNumberOfLevels() {
        long spentAtThirty = ExperienceMath.atLevel(30) - ExperienceMath.atLevel(27);
        assertEquals(306, spentAtThirty);
        assertEquals(91, ExperienceMath.percentageRefund(spentAtThirty, 30));
        long spentAtForty = ExperienceMath.atLevel(40) - ExperienceMath.atLevel(37);
        assertEquals(552, spentAtForty);
        assertEquals(165, ExperienceMath.percentageRefund(spentAtForty, 30));
        assertEquals(0, ExperienceMath.percentageRefund(0, 30));
        assertEquals(0, ExperienceMath.percentageRefund(-10, 30));
        assertEquals(0, ExperienceMath.percentageRefund(306, 0));
        assertEquals(306, ExperienceMath.percentageRefund(306, 100));
    }
}
