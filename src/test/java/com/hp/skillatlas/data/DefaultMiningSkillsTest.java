package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMiningSkillsTest {
    @Test
    void newBranchesSurviveSerializationWithCorrectPositionsAndPrerequisites() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        SkillNode haste = d.nodes.get("haste");
        SkillNode minerals = d.nodes.get("stone_minerals");
        SkillNode experience = d.nodes.get("mining_experience");

        assertTrue(minerals.x < haste.x && minerals.y > haste.y);
        assertTrue(experience.x > haste.x && experience.y > haste.y);
        for (SkillNode n : List.of(minerals, experience)) {
            assertEquals("mining", n.category);
            assertEquals("mining", d.poolOf(n));
            assertEquals(5, n.maxLevel());
            assertEquals(1, n.prerequisites.size());
            assertEquals("haste", n.prerequisites.get(0).node);
            assertTrue(n.prerequisites.get(0).line);
            assertTrue(n.levels.stream().allMatch(level -> level.items.isEmpty()));
        }
    }

    @Test
    void fivePointsReachTenPercentMineralsAndDoubleOreExperience() {
        AtlasDefinition d = DefaultAtlas.create();
        for (String id : List.of("stone_minerals", "mining_experience")) {
            SkillNode n = d.nodes.get(id);
            PlayerProgress p = new PlayerProgress();
            p.setLevel("haste", 1);
            p.setPoints("mining", 5);
            for (int level = 0; level < n.maxLevel(); level++) {
                assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true));
                SkillLevel next = n.levels.get(level);
                assertEquals(1, next.cost);
                p.addPoints("mining", -next.cost);
                p.setLevel(id, level + 1);
            }
            assertEquals(0, p.points("mining"));
            assertEquals(AtlasLogic.Check.MAXED, AtlasLogic.check(d, n, p, items -> true));
            Reward reward = n.levels.get(p.level(id) - 1).rewards.get(0);
            assertEquals(RewardType.ATTRIBUTE, reward.type);
            assertEquals(RewardOperation.ADD, reward.operation);
            if (id.equals("stone_minerals")) {
                assertEquals("skill_atlas:stone_mineral_chance", reward.target);
                assertEquals(10.0D, reward.amount);
            } else {
                assertEquals("skill_atlas:mining_experience", reward.target);
                assertEquals(100.0D, reward.amount);
            }
        }
    }
}
