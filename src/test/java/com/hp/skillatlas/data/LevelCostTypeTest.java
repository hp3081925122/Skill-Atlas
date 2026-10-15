package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LevelCostTypeTest {
    @Test
    void oneSkillCanSpendExperienceThenPointsInTheSameCategory() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode n = d.nodes.get("haste");
        n.levels.get(0).experience = true;
        n.levels.get(0).cost = 10;
        n.levels.get(1).experience = false;
        n.levels.get(1).cost = 20;
        d = AtlasDefinition.fromJson(d.toJson());
        n = d.nodes.get("haste");
        PlayerProgress p = new PlayerProgress();
        p.setPoints("mining", 10);
        assertEquals(AtlasLogic.Check.NO_EXPERIENCE, AtlasLogic.check(d, n, p, items -> true, 9));
        assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true, 10));
        p.setLevel("haste", 1);
        assertEquals(AtlasLogic.Check.NO_POINTS, AtlasLogic.check(d, n, p, items -> true, 1_000_000));
        p.setPoints("mining", 20);
        assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true, 0));
    }

    @Test
    void selectedTypeSurvivesCopyAndCategoryPoolChanges() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode n = d.nodes.get("enchanting_cap");
        d.categories.get("enchanting").pool = "mining";
        d.validate();
        assertTrue(d.usesExperience(n, n.levels.get(0)));
        assertTrue(n.levels.get(0).copy().experience);
        n.levels.get(1).experience = false;
        assertFalse(n.levels.get(1).copy().experience);
        AtlasDefinition restored = AtlasDefinition.fromJson(d.toJson());
        assertTrue(restored.nodes.get(n.id).levels.get(0).experience);
        assertFalse(restored.nodes.get(n.id).levels.get(1).experience);
    }

    @Test
    void oldFilesPreserveExperienceForPoollessCategoriesAndPointsElsewhere() {
        JsonObject legacy = DefaultAtlas.create().toJson();
        for (JsonElement node : legacy.getAsJsonArray("nodes")) {
            for (JsonElement level : node.getAsJsonObject().getAsJsonArray("levels")) {
                level.getAsJsonObject().remove("experience");
            }
        }
        AtlasDefinition d = AtlasDefinition.fromJson(legacy);
        assertTrue(d.nodes.get("enchanting_cap").levels.stream().allMatch(level -> level.experience));
        assertTrue(d.nodes.get("haste").levels.stream().noneMatch(level -> level.experience));
        assertTrue(d.nodes.get("enchanting_cap").levels.get(0).toJson().get("experience").getAsBoolean());
    }

    @Test
    void paidPointLevelsRequireAPoolButFreeLevelsDoNot() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillLevel level = d.nodes.get("enchanting_cap").levels.get(0);
        level.experience = false;
        AtlasException error = assertThrows(AtlasException.class, d::validate);
        assertEquals("skill_atlas.error.no_skill_pool", error.key());
        level.cost = 0;
        assertDoesNotThrow(d::validate);
        assertFalse(level.experience);
    }

    @Test
    void mixedCurrencyRefundsFollowPaymentsRatherThanCurrentLevelSettings() {
        PlayerProgress p = new PlayerProgress();
        p.recordPayment("haste", PlayerProgress.EXPERIENCE, 10);
        p.recordPayment("haste", "mining", 20);
        p.setLevel("haste", 2);
        p.truncate("haste", 0);
        assertEquals(10, p.takeExperienceRefund());
        assertEquals(20, p.points("mining"));
        assertEquals(0, p.points(PlayerProgress.EXPERIENCE));
    }
}
