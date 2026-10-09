package com.hp.skillatlas;

import com.hp.skillatlas.client.DisplayNames;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class DisplayNamesTest {
    @Test void existingDefaultNamesResolveWithoutEnglishSuffixes() {
        Map<String, String> translations = Map.of("skill_atlas.default.combat", "战斗", "skill_atlas.default.general", "通用", "skill_atlas.default.strength", "力量");
        for (String old : new String[]{"战斗 / Combat", "战斗/combat", "战斗/ Combat", "战斗 /combat"}) {
            assertEquals("战斗", DisplayNames.resolve(old, translations::get));
        }
        assertEquals("通用", DisplayNames.resolve("通用 / General", translations::get));
        assertEquals("力量", DisplayNames.resolve("力量 / Strength", translations::get));
    }

    @Test void customNamesAndIdentifiersAreNeverSplitOrRewritten() {
        for (String custom : new String[]{"攻击/防御", "Alpha / Beta", "mod:textures/skill.png", "custom_combat", "战斗 / Custom"}) {
            assertEquals(custom, DisplayNames.resolve(custom, key -> fail("Unexpected translation: " + key)));
        }
    }

    @Test void canonicalAndLegacyDefaultsFollowTheSelectedLanguage() {
        Map<String, String> translations = Map.of("skill_atlas.default.combat", "Combat", "skill_atlas.default.survival", "Survival", "skill_atlas.default.strength_tooltip", "Increase attack damage");
        assertEquals("Combat", DisplayNames.resolve("战斗", translations::get));
        assertEquals("Combat", DisplayNames.resolve("战斗 / Combat", translations::get));
        assertEquals("Survival", DisplayNames.resolve("生存/survival", translations::get));
        assertEquals("Increase attack damage", DisplayNames.resolve("提升攻击力 / Increase attack damage", translations::get));
    }
}
