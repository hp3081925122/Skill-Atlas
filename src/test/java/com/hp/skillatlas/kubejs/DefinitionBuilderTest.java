package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.data.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionBuilderTest {
    private AtlasDefinition base() {
        AtlasDefinition base = new AtlasDefinition();
        base.pools.put("training", new PointPool("training", "Training", 0x55ffff, 0));
        base.categories.put("training", new Category("training", "Training", Icon.item("minecraft:book"), "training"));
        return base;
    }

    @Test void overlayPreservesBaseAndRewardLevelsRemainIndependent() {
        AtlasDefinition base = base(); DefinitionsEvent e = new DefinitionsEvent(base);
        e.createNode("study").category("training").level(1, r -> r.attributeReward("minecraft:generic.attack_damage", 1, "add")).level(2, r -> r.attributeReward("minecraft:generic.attack_damage", 2, "add"));
        AtlasDefinition result = e.finish();
        assertTrue(base.nodes.isEmpty()); assertEquals(2, result.nodes.get("study").maxLevel());
        assertEquals(1, result.nodes.get("study").levels.get(0).rewards.size());
        assertEquals(2, result.nodes.get("study").levels.get(1).rewards.get(0).amount);
    }

    @Test void finishPreservesAtlasTextsWithoutSharingMutableState() {
        AtlasDefinition base = base();
        String key = AtlasTexts.key("category", "training", "name");
        base.categories.get("training").name = key;
        base.texts.put("en_us", key, "Training");
        base.texts.put("zh_cn", key, "训练");
        DefinitionsEvent event = new DefinitionsEvent(base);
        base.texts.put("zh_cn", key, "原定义已修改");
        AtlasDefinition result = event.finish();
        assertEquals("训练", result.texts.resolve(key, "zh_cn"), "KubeJS definitions must preserve atlas translations");
        assertEquals("Training", result.texts.display(key, "en_us"));
        result.texts.put("zh_cn", key, "结果已修改");
        assertEquals("原定义已修改", base.texts.resolve(key, "zh_cn"));
    }

    @Test void invalidOverlayDoesNotMutateBase() {
        AtlasDefinition base = base(); DefinitionsEvent e = new DefinitionsEvent(base);
        e.createNode("study").category("missing").level(1, r -> {});
        assertThrows(AtlasException.class, e::finish); assertTrue(base.nodes.isEmpty());
    }

    @Test void closedBuildersCannotMutateCommittedDefinition() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        NodeBuilder node = e.createNode("study").category("training").level(1, r -> {});
        AtlasDefinition result = e.finish();
        assertThrows(IllegalStateException.class, () -> node.name("changed"));
        assertEquals("study", result.nodes.get("study").name);
    }

    @Test void strictPrerequisiteMustBeReachable() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        e.createNode("source").category("training").level(1, r -> {});
        e.createNode("target").category("training").requiresLevelGreaterThan("source", 1).level(1, r -> {});
        assertThrows(AtlasException.class, e::finish);
    }

    @Test void costsHaveExplicitCurrenciesAndDeliveryData() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        e.createNode("study").category("training").level(1, r -> r.cost(Map.of("type", "experience", "amount", 100)).item("minecraft:diamond", 2, "{test:1b}"));
        SkillLevel rank = e.finish().nodes.get("study").levels.get(0);
        assertTrue(rank.experience); assertEquals(100, rank.cost); assertEquals(2, rank.items.get(0).count); assertEquals("{test:1b}", rank.items.get(0).nbt);
    }

    @Test void duplicateAndNamespacedAtlasIdsAreRejected() {
        DefinitionsEvent e = new DefinitionsEvent(base()); e.createNode("study");
        assertThrows(IllegalArgumentException.class, () -> e.createNode("study"));
        assertThrows(IllegalArgumentException.class, () -> e.createNode("pack:study"));
    }

    @Test void unsafeNumbersCannotBecomeCostsOrCoordinates() {
        DefinitionsEvent e = new DefinitionsEvent(base()); NodeBuilder node = e.createNode("study").category("training");
        assertThrows(IllegalArgumentException.class, () -> node.position(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> node.level(1, r -> r.cost(Map.of("type", "experience", "amount", 1.5))));
        assertThrows(IllegalArgumentException.class, () -> Values.integer(Double.POSITIVE_INFINITY, 0, Long.MAX_VALUE));
    }

    @Test void snapshotsAreDetachedAndImmutable() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        Map<?, ?> snapshot = (Map<?, ?>)e.getSnapshot();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        e.createNode("study").category("training").level(1, r -> {});
        assertTrue(((List<?>)snapshot.get("nodes")).isEmpty());
    }

    @Test void noPoolCategoryInheritsExperienceCurrency() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        e.createCategory("magic");
        e.createNode("magic_study").category("magic").level(1, r -> {});
        AtlasDefinition result = e.finish();
        assertTrue(result.usesExperience(result.nodes.get("magic_study"), result.nodes.get("magic_study").levels.get(0)));
    }

    @Test void singleIconEntryAcceptsStringAndStructuredTexture() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        e.createNode("item_icon").category("training").icon("minecraft:diamond").level(1, r -> {});
        e.createNode("texture_icon").category("training").icon(Map.of("type", "texture", "id", "minecraft:textures/item/diamond.png")).level(1, r -> {});
        AtlasDefinition result = e.finish();
        assertEquals("minecraft:diamond", result.nodes.get("item_icon").icon.id);
        assertTrue(result.nodes.get("texture_icon").icon.isTexture());
    }
    @Test void choiceGroupIsValidatedAndClearable() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        e.createNode("left").category("training").choiceGroup("path").level(1, r -> {});
        e.createNode("right").category("training").choiceGroup("path").level(1, r -> {});
        NodeBuilder other = e.createNode("other").category("training").choiceGroup("path").level(1, r -> {});
        assertThrows(IllegalArgumentException.class, () -> other.choiceGroup("Bad Group"));
        other.choiceGroup("");
        AtlasDefinition result = e.finish();
        assertEquals("path", result.nodes.get("left").choiceGroup);
        assertEquals("", result.nodes.get("other").choiceGroup);
        PlayerProgress p = new PlayerProgress();
        p.setLevel("left", 1);
        assertEquals(AtlasLogic.Check.EXCLUDED, AtlasLogic.check(result, result.nodes.get("right"), p, items -> true, 1_000_000));
    }
}
