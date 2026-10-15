package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FarmingTreeTest {
    @Test
    void fiveSingleLevelNodesKeepFiveTotalExperienceAndBranchLikeAnEarOfWheat() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        Set<String> positions = new HashSet<>();
        double total = 0;
        for (int i = 1; i <= 5; i++) {
            SkillNode n = d.nodes.get("harvest_experience_" + i);
            assertTrue(positions.add(n.x + "," + n.y));
            assertFalse(n.size > 1.0D);
            assertEquals(1, n.maxLevel());
            SkillLevel level = n.levels.get(0);
            assertEquals(1, level.cost);
            assertFalse(d.usesExperience(n, level));
            assertEquals(RewardOperation.ADD, level.rewards.get(0).operation);
            assertEquals("skill_atlas:harvest_experience", level.rewards.get(0).target);
            total += level.rewards.get(0).amount;
            if (i == 1) {
                assertTrue(n.prerequisites.isEmpty());
            } else {
                assertEquals("harvest_experience_" + (i == 5 ? 3 : i - 1), n.prerequisites.get(0).node);
            }
        }
        assertEquals(5, total);
        assertFalse(d.nodes.containsKey("harvest_experience"));
    }

    @Test
    void chosenMajorsRequireTheirOwnBranchAndOnePointPlusTheChosenItem() {
        AtlasDefinition d = DefaultAtlas.create();
        String[] ids = {"crop_replanting", "harvest_yield"};
        String[] items = {"minecraft:golden_hoe", "minecraft:hay_block"};
        double[] amounts = {1, 30};
        PlayerProgress p = new PlayerProgress();
        p.setPoints("farming", 1);
        for (int i = 0; i < ids.length; i++) {
            SkillNode n = d.nodes.get(ids[i]);
            assertTrue(n.size > 1.0D);
            assertEquals(1, n.maxLevel());
            assertEquals("harvest_experience_" + (i + 4), n.prerequisites.get(0).node);
            SkillLevel level = n.levels.get(0);
            assertEquals(1, level.cost);
            assertEquals("skill_atlas:" + ids[i], level.rewards.get(0).target);
            assertEquals(amounts[i], level.rewards.get(0).amount);
            assertEquals(items[i], level.items.get(0).item);
            assertEquals(1, level.items.get(0).count);
            assertEquals(AtlasLogic.Check.LOCKED, AtlasLogic.check(d, n, p, costs -> true));
            p.setLevel(n.prerequisites.get(0).node, 1);
            assertEquals(AtlasLogic.Check.NO_ITEMS, AtlasLogic.check(d, n, p, costs -> false));
            assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, costs -> true));
        }
    }
}
