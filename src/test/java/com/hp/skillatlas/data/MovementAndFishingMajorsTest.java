package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovementAndFishingMajorsTest {
    @Test
    void elytraMajorRequiresAllThreeRunningBranchesAndAnElytra() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        SkillNode n = d.nodes.get("elytra_speed");
        assertTrue(n.size > 1.0D);
        assertTrue(n.requireAll);
        assertEquals(3, n.prerequisites.size());
        PlayerProgress p = new PlayerProgress();
        p.setPoints("running", 1);
        for (String id : List.of("running_vitality", "light_landing", "underwater_breathing")) {
            assertEquals(AtlasLogic.Check.LOCKED, AtlasLogic.check(d, n, p, items -> true));
            p.setLevel(id, 1);
            assertTrue(n.y > d.nodes.get(id).y);
        }
        assertEquals(AtlasLogic.Check.NO_ITEMS, AtlasLogic.check(d, n, p, items -> false));
        assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true));
        SkillLevel level = n.levels.get(0);
        assertEquals(1, level.cost);
        assertEquals("minecraft:elytra", level.items.get(0).item);
        assertEquals(1, level.items.get(0).count);
        assertEquals("skill_atlas:elytra_speed", level.rewards.get(0).target);
        assertEquals(50, level.rewards.get(0).amount);
    }

    @Test
    void fishingMajorsGrantOneLuckEachForSixteenEmeralds() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        assertEquals(4, d.nodesIn("fishing").size());
        for (int i = 1; i <= 2; i++) {
            SkillNode n = d.nodes.get("fishing_luck_major_" + i);
            assertTrue(n.size > 1.0D);
            assertEquals(i == 1 ? "fishing_lure" : "fishing_luck", n.prerequisites.get(0).node);
            SkillLevel level = n.levels.get(0);
            assertEquals(1, level.cost);
            assertEquals("minecraft:emerald", level.items.get(0).item);
            assertEquals(16, level.items.get(0).count);
            assertEquals("minecraft:generic.luck", level.rewards.get(0).target);
            assertEquals(RewardOperation.ADD, level.rewards.get(0).operation);
            assertEquals(1, level.rewards.get(0).amount);
        }
    }
}
