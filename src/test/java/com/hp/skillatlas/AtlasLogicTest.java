package com.hp.skillatlas;

import com.hp.skillatlas.core.*;
import com.hp.skillatlas.model.*;
import com.hp.skillatlas.client.TextSearch;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

public class AtlasLogicTest {
    private Definition definition() { return Definition.defaults(); }
    private PlayerProgress player(long points) { var p = new PlayerProgress(); p.balance("general").points = points; return p; }
    @Test void defaultDefinitionIsValid() { assertEquals(List.of(), AtlasLogic.validate(definition())); }
    @Test void rankRewardsReplaceRatherThanAccumulate() {
        var d = definition(); var p = player(10);
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 0)); var first = RewardPlan.build(d, p);
        assertEquals(1, first.size()); assertEquals(1, first.get(0).reward().amount);
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 1)); var second = RewardPlan.build(d, p);
        assertEquals(1, second.size()); assertEquals(2, second.get(0).reward().amount); assertEquals(first.get(0).key(), second.get(0).key());
        assertEquals(7, p.balance("general").points);
    }
    @Test void duplicateAndInsufficientRequestsDoNotCharge() {
        var d = definition(); var p = player(1);
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 0));
        assertEquals("stale", AtlasLogic.learn(d, p, "strength", 0));
        assertEquals("insufficient", AtlasLogic.learn(d, p, "strength", 1));
        assertEquals(0, p.balance("general").points); assertEquals(1, p.level("strength"));
    }
    @Test void concurrentRequestsChargeExactlyOnce() throws Exception {
        var d = definition(); var p = player(100); ExecutorService pool = Executors.newFixedThreadPool(8);
        try { List<Future<String>> results = new ArrayList<>(); for (int i = 0; i < 32; i++) results.add(pool.submit(() -> AtlasLogic.learn(d, p, "strength", 0)));
            int successes = 0; for (var result : results) if (result.get().equals("ok")) successes++;
            assertEquals(1, successes); assertEquals(99, p.balance("general").points); assertEquals(1, p.level("strength"));
        } finally { pool.shutdownNow(); }
    }
    @Test void crossCategoryPrerequisiteUsesRequiredRank() {
        var d = definition(); var p = player(10); d.nodes.get("miner").prerequisites.add(new Definition.Requirement("strength", 2));
        assertFalse(AtlasLogic.ready(d.nodes.get("miner"), p));
        AtlasLogic.learn(d, p, "strength", 0); assertFalse(AtlasLogic.ready(d.nodes.get("miner"), p));
        AtlasLogic.learn(d, p, "strength", 1); assertTrue(AtlasLogic.ready(d.nodes.get("miner"), p));
        assertEquals("ok", AtlasLogic.learn(d, p, "miner", 0));
    }
    @Test void anyAllAndHiddenSemantics() {
        var d = definition(); var p = player(10); var n = new Definition.Node(); n.hideUntilReady = true;
        n.prerequisites.add(new Definition.Requirement("strength", 1)); n.prerequisites.add(new Definition.Requirement("miner", 1));
        assertFalse(AtlasLogic.visible(n, p, false)); assertTrue(AtlasLogic.visible(n, p, true));
        AtlasLogic.learn(d, p, "strength", 0); assertFalse(AtlasLogic.ready(n, p));
        n.prerequisiteMode = "any"; assertTrue(AtlasLogic.ready(n, p)); assertTrue(AtlasLogic.visible(n, p, false));
    }
    @Test void cyclesMissingParentsAndInvalidRanksAreRejected() {
        var d = definition(); d.nodes.get("strength").prerequisites.add(new Definition.Requirement("miner", 1)); d.nodes.get("miner").prerequisites.add(new Definition.Requirement("strength", 1));
        assertTrue(AtlasLogic.validate(d).stream().anyMatch(e -> e.contains("cycle")));
        d.nodes.get("miner").prerequisites.clear(); d.nodes.get("strength").prerequisites.get(0).level = 2;
        assertFalse(AtlasLogic.validate(d).isEmpty()); d.nodes.get("strength").prerequisites.get(0).node = "absent"; assertFalse(AtlasLogic.validate(d).isEmpty());
    }
    @Test void nullAndNegativeFieldsAreRejectedWithoutThrowing() {
        var d = definition(); d.nodes.get("strength").levels.get(0).cost = -1; assertFalse(AtlasLogic.validate(d).isEmpty());
        d = definition(); d.nodes.get("strength").prerequisiteMode = null; assertFalse(AtlasLogic.validate(d).isEmpty());
        d = definition(); d.nodes.get("strength").levels.get(0).rewards.get(0).operation = null; assertFalse(AtlasLogic.validate(d).isEmpty());
    }
    @Test void recursiveResetRefundsOriginalPayments() {
        var d = definition(); var p = player(20); d.nodes.get("miner").prerequisites.add(new Definition.Requirement("strength", 2));
        AtlasLogic.learn(d, p, "strength", 0); AtlasLogic.learn(d, p, "strength", 1); AtlasLogic.learn(d, p, "miner", 0);
        d.nodes.get("strength").levels.get(0).cost = 100; d.nodes.get("miner").levels.get(0).cost = 100;
        AtlasLogic.reset(d, p, "strength"); assertTrue(p.learned.isEmpty()); assertEquals(20, p.balance("general").points);
    }
    @Test void categoryPoolChangesDoNotRedirectRefunds() {
        var d = definition(); var p = player(10); AtlasLogic.learn(d, p, "strength", 0);
        d.pools.put("combat", new Definition.Pool()); d.categories.get("combat").pool = "combat";
        AtlasLogic.reset(d, p, "strength"); assertEquals(10, p.balance("general").points); assertEquals(0, p.balance("combat").points);
    }
    @Test void publicationClampsLevelsAndCascadesInvalidChildren() {
        var d = definition(); var p = player(20); d.nodes.get("miner").prerequisites.add(new Definition.Requirement("strength", 2));
        AtlasLogic.learn(d, p, "strength", 0); AtlasLogic.learn(d, p, "strength", 1); AtlasLogic.learn(d, p, "miner", 0);
        d.nodes.get("strength").levels.remove(1); d.revision = 9; AtlasLogic.reconcile(d, p);
        assertEquals(1, p.level("strength")); assertEquals(0, p.level("miner")); assertEquals(19, p.balance("general").points); assertEquals(9, p.revision);
    }
    @Test void removingNodesRefundsAndDoesNotChargeCostIncreases() {
        var d = definition(); var p = player(10); AtlasLogic.learn(d, p, "strength", 0);
        d.nodes.get("strength").levels.get(0).cost = 9; AtlasLogic.reconcile(d, p); assertEquals(9, p.balance("general").points);
        d.nodes.remove("strength"); AtlasLogic.reconcile(d, p); assertEquals(10, p.balance("general").points); assertEquals(0, p.level("strength"));
    }
    @Test void independentRulesPreserveRemainders() {
        var d = definition(); var p = player(0); var r = d.rules.get("zombies"); r.threshold = 3; r.amount = 2;
        for (int i = 0; i < 8; i++) AtlasLogic.trigger(d, p, "zombies", r);
        assertEquals(4, p.balance("general").points); assertEquals(2L, p.counters.get("zombies"));
        var mine = d.rules.get("ores"); mine.threshold = 2; AtlasLogic.trigger(d, p, "ores", mine);
        assertEquals(1L, p.counters.get("ores")); assertEquals(2L, p.counters.get("zombies"));
    }
    @Test void experienceLevelsPreserveOverflow() {
        var pool = new Definition.Pool(); var balance = new PlayerProgress.Balance();
        AtlasLogic.experience(pool, balance, 475); assertEquals(3, balance.level); assertEquals(25, balance.experience); assertEquals(3, balance.points);
        AtlasLogic.experience(pool, balance, 225); assertEquals(4, balance.level); assertEquals(0, balance.experience); assertEquals(4, balance.points);
    }
    @Test void zeroGrowthAndLargeExperienceStayBounded() {
        var pool = new Definition.Pool(); pool.baseExperience = 1; pool.experienceGrowth = 0; var balance = new PlayerProgress.Balance();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> AtlasLogic.experience(pool, balance, AtlasLogic.LIMIT));
        assertEquals(1_000_000, balance.level); assertEquals(1_000_000, balance.points);
    }
    @Test void settingExperienceDoesNotGrantPointsAgain() {
        var p = new Definition.Pool(); var b = new PlayerProgress.Balance(); b.points = 9;
        AtlasLogic.setExperience(p, b, 475); assertEquals(3, b.level); assertEquals(25, b.experience); assertEquals(9, b.points);
        AtlasLogic.setExperience(p, b, 0); assertEquals(0, b.level); assertEquals(0, b.experience); assertEquals(9, b.points);
    }
    @Test void experienceRuleAwardsItsTargetPool() {
        var d = definition(); d.pools.put("mining", new Definition.Pool()); var p = player(0); var r = new Definition.Rule(); r.pool = "mining"; r.mode = "experience"; r.amount = 120;
        AtlasLogic.trigger(d, p, "mining_xp", r); assertEquals(1, p.balance("mining").points); assertEquals(20, p.balance("mining").experience); assertEquals(0, p.balance("general").points);
    }
    @Test void clipboardRemapsInternalDependenciesAndLines() {
        var d = definition(); d.nodes.get("miner").category = "combat"; d.nodes.get("miner").prerequisites.add(new Definition.Requirement("strength", 2)); d.lines.add(new Definition.Line("strength", "miner"));
        NodeClipboard clipboard = new NodeClipboard(); clipboard.copy(d, Set.of("strength", "miner")); Set<String> ids = clipboard.paste(d, "mining", 500, 600, false);
        assertEquals(2, ids.size()); assertTrue(ids.stream().allMatch(id -> d.nodes.get(id).category.equals("mining")));
        String child = ids.stream().filter(id -> !d.nodes.get(id).prerequisites.isEmpty()).findFirst().orElseThrow();
        assertTrue(ids.contains(d.nodes.get(child).prerequisites.get(0).node)); assertEquals(2, d.lines.size());
        assertTrue(ids.contains(d.lines.get(1).from)); assertTrue(ids.contains(d.lines.get(1).to));
        assertEquals(List.of(), AtlasLogic.validate(d));
    }
    @Test void externalPrerequisitesAreExplicitlyOptional() {
        var d = definition(); d.nodes.get("miner").prerequisites.add(new Definition.Requirement("strength", 1)); var clipboard = new NodeClipboard(); clipboard.copy(d, Set.of("miner"));
        String internal = clipboard.paste(d, "mining", 10, 10, false).iterator().next(); assertTrue(d.nodes.get(internal).prerequisites.isEmpty());
        String external = clipboard.paste(d, "mining", 20, 20, true).iterator().next(); assertEquals("strength", d.nodes.get(external).prerequisites.get(0).node);
    }
    @Test void draftCopyCannotModifyPublishedData() {
        var d = definition(); var copy = d.copy(); copy.nodes.get("strength").levels.get(0).rewards.get(0).amount = 77;
        assertEquals(1, d.nodes.get("strength").levels.get(0).rewards.get(0).amount);
    }
    @Test void persistedProgressRoundTripsPaymentHistoryAndCounters() {
        var d = definition(); var p = player(10); AtlasLogic.learn(d, p, "strength", 0); p.counters.put("zombies", 7L);
        p.balance("general").experience = 23; p.balance("general").level = 4;
        var restored = StrictJson.parse(Definition.JSON.toJson(p), PlayerProgress.class);
        assertEquals(1, restored.level("strength")); assertEquals(7L, restored.counters.get("zombies")); assertEquals(23, restored.balance("general").experience);
        AtlasLogic.reset(d, restored, "*"); assertEquals(10, restored.balance("general").points);
    }
    @Test void strictJsonRejectsDuplicateIdsAndTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse("{\"nodes\":{\"a\":{},\"a\":{}}}", Definition.class));
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse("{}{}", Definition.class));
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse("[".repeat(70) + "0" + "]".repeat(70), Object.class));
    }
    @Test void pinyinNameTooltipInitialsAndFormattingMatch() {
        String index = TextSearch.index("§6力量提升 提高挖掘速度 strength li liang");
        assertTrue(TextSearch.matches(index, "力量")); assertTrue(TextSearch.matches(index, "li liang")); assertTrue(TextSearch.matches(index, "llts"));
        assertTrue(TextSearch.matches(index, "wajuesudu")); assertTrue(TextSearch.matches(index, "STRENGTH")); assertFalse(TextSearch.matches(index, "不存在"));
    }
    @Test void fragmentedSnapshotRoundTripsUtf8AndRejectsReordering() {
        String text = "技能图谱".repeat(30000); byte[] bytes = text.getBytes(StandardCharsets.UTF_8); ChunkAssembler assembler = new ChunkAssembler(); UUID sender = UUID.randomUUID(); int total = (bytes.length + ChunkAssembler.CHUNK - 1) / ChunkAssembler.CHUNK; String actual = null;
        for (int i = 0; i < total; i++) actual = assembler.accept(sender, 1, i, total, Arrays.copyOfRange(bytes, i * ChunkAssembler.CHUNK, Math.min(bytes.length, (i + 1) * ChunkAssembler.CHUNK)), 1000);
        assertEquals(text, actual); assertThrows(IllegalArgumentException.class, () -> assembler.accept(sender, 2, 1, 2, new byte[10], 1001));
    }
    @Test void thousandNodeDefinitionValidationAndSearchFinishQuickly() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var d = definition(); d.nodes.clear();
            for (int i = 0; i < 1000; i++) { var n = new Definition.Node(); n.name = "技能" + i; n.tooltip = "提高攻击与挖掘能力"; n.x = i % 40 * 70; n.y = i / 40 * 70; if (i > 0) n.prerequisites.add(new Definition.Requirement("node_" + (i - 1), 1)); d.nodes.put("node_" + i, n); }
            assertTrue(AtlasLogic.validate(d).isEmpty()); var indexes = d.nodes.values().stream().map(n -> TextSearch.index(n.name + n.tooltip)).toList();
            assertEquals(1000, indexes.stream().filter(s -> TextSearch.matches(s, "wajue")).count()); assertTrue(Definition.JSON.toJson(d).getBytes(StandardCharsets.UTF_8).length < 4_000_000);
        });
    }
}
