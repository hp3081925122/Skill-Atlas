package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrerequisiteLevelTest {
    @Test
    void strictThresholdRejectsEqualityAndAcceptsTheNextLevel() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode n = d.nodes.get("lapis_saving");
        n.prerequisites.get(0).level = 5;
        PlayerProgress p = new PlayerProgress();
        for (int level : new int[]{0, 1, 5}) {
            p.setLevel("enchanting_cap", level);
            assertFalse(AtlasLogic.prerequisitesMet(d, n, p));
            assertEquals(AtlasLogic.Check.LOCKED, AtlasLogic.check(d, n, p, items -> true, 1_000));
        }
        p.setLevel("enchanting_cap", 6);
        assertTrue(AtlasLogic.prerequisitesMet(d, n, p));
        assertEquals(AtlasLogic.Check.OK, AtlasLogic.check(d, n, p, items -> true, 1_000));
    }

    @Test
    void allAndAnyUseEachPrerequisitesOwnThresholdEvenWithoutALine() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode n = d.nodes.get("fishing_luck_major_1");
        n.prerequisites.clear();
        n.prerequisites.add(new Prerequisite("fishing_lure", false, 2));
        n.prerequisites.add(new Prerequisite("fishing_luck", true, 1));
        PlayerProgress p = new PlayerProgress();
        p.setLevel("fishing_lure", 2);
        p.setLevel("fishing_luck", 2);
        assertFalse(AtlasLogic.prerequisitesMet(d, n, p));
        n.requireAll = false;
        assertTrue(AtlasLogic.prerequisitesMet(d, n, p));
        p.setLevel("fishing_luck", 1);
        assertFalse(AtlasLogic.prerequisitesMet(d, n, p));
        p.setLevel("fishing_lure", 3);
        assertTrue(AtlasLogic.prerequisitesMet(d, n, p));
        n.requireAll = true;
        assertFalse(AtlasLogic.prerequisitesMet(d, n, p));
        p.setLevel("fishing_luck", 2);
        assertTrue(AtlasLogic.prerequisitesMet(d, n, p));
    }

    @Test
    void hiddenNodesFollowTheSameThresholdAndEarnedNodesStayVisible() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode n = d.nodes.get("lapis_saving");
        n.hidden = true;
        n.prerequisites.get(0).level = 5;
        PlayerProgress p = new PlayerProgress();
        p.setLevel("enchanting_cap", 5);
        assertFalse(AtlasLogic.visible(d, n, p));
        p.setLevel("enchanting_cap", 6);
        assertTrue(AtlasLogic.visible(d, n, p));
        p.setLevel("enchanting_cap", 0);
        p.setLevel(n.id, 1);
        assertTrue(AtlasLogic.visible(d, n, p));
    }

    @Test
    void copiesJsonAndEditOperationsPreserveTheThreshold() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode n = d.nodes.get("lapis_saving").copy();
        n.prerequisites.get(0).level = 17;
        n.prerequisites.get(0).line = false;
        JsonArray ops = new JsonArray();
        ops.add(EditOps.nodeOp(n, false));
        EditOps.apply(d, ops);
        d.validate();
        AtlasDefinition restored = AtlasDefinition.fromJson(d.toJson()).copy();
        Prerequisite pre = restored.nodes.get(n.id).prerequisites.get(0);
        assertEquals(17, pre.level);
        assertFalse(pre.line);
        assertEquals(17, pre.toJson().get("level").getAsInt());
        pre.level = 20;
        assertEquals(17, d.nodes.get(n.id).prerequisites.get(0).level);
    }

    @Test
    void rejectsOutOfRangeFractionalAndUnreachableThresholds() {
        for (double value : new double[]{-1, 100, 1.5}) {
            JsonObject json = new Prerequisite("enchanting_cap", true).toJson();
            json.addProperty("level", value);
            assertThrows(AtlasException.class, () -> Prerequisite.fromJson(json));
        }
        AtlasDefinition d = DefaultAtlas.create();
        Prerequisite pre = d.nodes.get("lapis_saving").prerequisites.get(0);
        for (int value : new int[]{-1, 30, 100}) {
            pre.level = value;
            AtlasException error = assertThrows(AtlasException.class, d::validate);
            assertEquals("skill_atlas.error.prerequisite_level", error.key());
        }
        pre.level = 29;
        assertDoesNotThrow(d::validate);
    }

    @Test
    void defaultThresholdStillRequiresOneLearnedLevel() {
        Prerequisite pre = new Prerequisite("enchanting_cap", true);
        PlayerProgress p = new PlayerProgress();
        assertEquals(0, pre.level);
        assertFalse(pre.met(p));
        p.setLevel(pre.node, 1);
        assertTrue(pre.met(p));
        assertTrue(AtlasLogic.prerequisitesMet(DefaultAtlas.create(), new SkillNode(), p));
    }
}
