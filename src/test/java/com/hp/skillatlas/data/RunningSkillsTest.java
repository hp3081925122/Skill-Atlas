package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunningSkillsTest {
    @Test
    void fiveNodeBranchesReachTheRequestedBonusesAndUnlockTheirMajorNodes() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        assertEquals(19, d.nodesIn("running").size());
        assertEquals("running", d.categories.get("running").pool);
        String[][] branches = {
                {"running_speed", "running_vitality", "minecraft:generic.movement_speed", "minecraft:diamond_boots"},
                {"jump_height", "light_landing", "skill_atlas:jump_height", "minecraft:feather"},
                {"swim_speed", "underwater_breathing", "forge:swim_speed", "minecraft:turtle_helmet"}
        };
        for (String[] branch : branches) {
            PlayerProgress p = new PlayerProgress();
            p.setPoints("running", 6);
            SkillNode major = d.nodes.get(branch[1]);
            double total = 0;
            for (int i = 1; i <= 5; i++) {
                assertEquals(AtlasLogic.Check.LOCKED, AtlasLogic.check(d, major, p, items -> true));
                SkillNode n = d.nodes.get(branch[0] + "_" + i);
                assertFalse(n.size > 1.0D);
                assertEquals(1, n.maxLevel());
                assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true));
                SkillLevel level = n.levels.get(0);
                assertEquals(1, level.cost);
                assertFalse(d.usesExperience(n, level));
                assertTrue(level.items.isEmpty());
                Reward reward = level.rewards.get(0);
                assertEquals(branch[2], reward.target);
                assertEquals(branch[0].equals("jump_height") ? RewardOperation.ADD : RewardOperation.MULTIPLY_BASE, reward.operation);
                total += reward.amount;
                p.addPoints("running", -1);
                p.setLevel(n.id, 1);
            }
            assertEquals(1, total, 1e-12);
            assertTrue(major.size > 1.0D);
            assertEquals(AtlasLogic.Check.NO_ITEMS, AtlasLogic.check(d, major, p, items -> false));
            assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, major, p, items ->
                    items.size() == 1 && items.get(0).item.equals(branch[3]) && items.get(0).count == 1));
            assertEquals(1, major.levels.get(0).cost);
        }
        assertEquals(1, d.nodes.get("running_vitality").levels.get(0).rewards.get(0).amount);
        assertEquals("forge:step_height_addition", d.nodes.get("running_vitality").levels.get(0).rewards.get(0).target);
        assertEquals(40, d.nodes.get("light_landing").levels.get(0).rewards.get(0).amount);
        Reward breathing = d.nodes.get("underwater_breathing").levels.get(0).rewards.get(0);
        assertEquals(RewardType.EFFECT, breathing.type);
        assertEquals("minecraft:water_breathing", breathing.target);
        assertTrue(breathing.duration > breathing.interval);
    }

    @Test
    void movementThresholdUsesCentimetersWithoutAffectingOtherEarningMethods() {
        AtlasDefinition d = AtlasDefinition.fromJson(DefaultAtlas.create().toJson());
        PointPool running = d.pools.get("running");
        assertEquals(RuleType.MOVEMENT, running.method);
        assertEquals(100, running.count);
        assertEquals(10_000, running.counterTarget());
        assertEquals(1, running.points);
        assertEquals(0, running.initial);
        running.count = 1_000_000_000;
        assertEquals(100_000_000_000L, running.counterTarget());
        for (String id : List.of("combat", "mining", "farming", "fishing")) {
            PointPool pool = d.pools.get(id);
            assertEquals(pool.count, pool.counterTarget());
        }
    }

    @Test
    void jumpIncreasesHeightByBlocksRatherThanByVelocity() {
        for (double gravity : new double[]{0.04, 0.08, 0.16}) {
            for (double original : new double[]{0.21, 0.42F, 0.62, 0.92}) {
                for (int rank = 1; rank <= 5; rank++) {
                    double extra = rank * 0.2;
                    double velocity = MovementMath.jumpVelocity(original, extra, gravity);
                    assertEquals(simulatedHeight(original, gravity) + extra, simulatedHeight(velocity, gravity), 1e-8);
                    assertTrue(velocity > original);
                }
            }
        }
        assertEquals(0.42, MovementMath.jumpVelocity(0.42, 0, 0.08));
        assertEquals(0.42, MovementMath.jumpVelocity(0.42, 1, 0));
    }

    private static double simulatedHeight(double velocity, double gravity) {
        double height = 0;
        while (velocity > 0) {
            height += velocity;
            velocity = (velocity - gravity) * (double) 0.98F;
        }
        return height;
    }
}
