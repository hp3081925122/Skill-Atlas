package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmeltingSkillsTest {
    @Test
    void fifteenSpeedNodesFormOneChainAndUnlockThreeSeparateMajorRewards() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        assertEquals(18, d.nodesIn("smelting").size());
        PointPool pool = d.pools.get("smelting");
        assertEquals(RuleType.SMELT, pool.method);
        assertEquals(8, pool.count);
        assertEquals(1, pool.points);
        assertEquals("smelting", d.categories.get("smelting").pool);
        PlayerProgress p = new PlayerProgress();
        p.setPoints("smelting", 18);
        double speed = 0;
        for (int i = 1; i <= 15; i++) {
            SkillNode n = d.nodes.get("smelting_speed_" + i);
            assertEquals(1, n.maxLevel());
            if (i > 1) {
                assertEquals("smelting_speed_" + (i - 1), n.prerequisites.get(0).node);
            }
            assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true));
            Reward r = n.levels.get(0).rewards.get(0);
            assertEquals("skill_atlas:smelting_speed", r.target);
            assertEquals(RewardOperation.ADD, r.operation);
            speed += r.amount;
            assertEquals(1, n.levels.get(0).cost);
            assertTrue(n.levels.get(0).items.isEmpty());
            p.setLevel(n.id, 1);
            p.addPoints("smelting", -1);
        }
        assertEquals(150, speed);
        String[][] majors = {
                {"raw_ore_doubling", "5", "skill_atlas:raw_ore_double_chance", "30", "minecraft:ancient_debris"},
                {"fuel_efficiency", "10", "skill_atlas:fuel_efficiency", "50", "minecraft:coal_block"},
                {"smelting_experience", "15", "skill_atlas:smelting_experience", "100", "minecraft:experience_bottle"}
        };
        for (String[] data : majors) {
            SkillNode n = d.nodes.get(data[0]);
            assertTrue(n.size > 1.0D);
            assertEquals("smelting_speed_" + data[1], n.prerequisites.get(0).node);
            SkillLevel level = n.levels.get(0);
            assertEquals(1, level.cost);
            assertEquals(data[2], level.rewards.get(0).target);
            assertEquals(Double.parseDouble(data[3]), level.rewards.get(0).amount);
            assertEquals(1, level.items.size());
            assertEquals(data[4], level.items.get(0).item);
            assertEquals(1, level.items.get(0).count);
            assertEquals(AtlasLogic.Check.NO_ITEMS, AtlasLogic.check(d, n, p, items -> false));
            assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true));
        }
    }
}
