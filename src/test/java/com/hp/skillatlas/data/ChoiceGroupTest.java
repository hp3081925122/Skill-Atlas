package com.hp.skillatlas.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChoiceGroupTest {
    @Test
    void learningOneChoiceExcludesTheOthersUntilReset() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode a = d.nodes.get("lapis_saving");
        SkillNode b = d.nodes.get("fishing_luck_major_1");
        a.prerequisites.clear();
        b.prerequisites.clear();
        a.choiceGroup = "path";
        b.choiceGroup = "path";
        PlayerProgress p = new PlayerProgress();
        assertNull(AtlasLogic.excludedBy(d, a, p));
        assertNotEquals(AtlasLogic.Check.EXCLUDED, AtlasLogic.check(d, b, p, items -> true, 1_000_000));
        p.setLevel(a.id, 1);
        assertSame(a, AtlasLogic.excludedBy(d, b, p));
        assertEquals(AtlasLogic.Check.EXCLUDED, AtlasLogic.check(d, b, p, items -> true, 1_000_000));
        assertEquals(AtlasLogic.State.LOCKED, AtlasLogic.state(d, b, p, items -> true, 1_000_000));
        assertNull(AtlasLogic.excludedBy(d, a, p));
        assertEquals(1, AtlasLogic.choices(d, a).size());
        p.setLevel(a.id, 0);
        assertNull(AtlasLogic.excludedBy(d, b, p));
    }

    @Test
    void choiceGroupRoundTripsAndRejectsBadIds() {
        AtlasDefinition d = DefaultAtlas.create();
        SkillNode a = d.nodes.get("lapis_saving");
        assertFalse(a.toJson().has("choice_group"));
        a.choiceGroup = "warrior_path";
        SkillNode back = SkillNode.fromJson(a.toJson());
        assertEquals("warrior_path", back.choiceGroup);
        assertEquals("warrior_path", back.copy().choiceGroup);
        JsonObject bad = a.toJson();
        bad.addProperty("choice_group", "Bad Group");
        assertThrows(AtlasException.class, () -> SkillNode.fromJson(bad));
    }
}
