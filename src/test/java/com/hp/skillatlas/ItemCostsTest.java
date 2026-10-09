package com.hp.skillatlas;

import com.hp.skillatlas.core.*;
import com.hp.skillatlas.model.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class ItemCostsTest {
    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion(); var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped"); bootstrapped.setAccessible(true); bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
    }
    private Definition.ItemCost cost(String item, int count, String nbt) {
        var cost = new Definition.ItemCost(); cost.item = item; cost.count = count; cost.nbt = nbt; return cost;
    }
    private PlayerProgress player(long points) { var p = new PlayerProgress(); p.balance("general").points = points; return p; }
    @Test void oldJsonInheritsCategoryWithoutItemRequirements() {
        var d = Definition.defaults(); var json = Definition.JSON.toJsonTree(d).getAsJsonObject();
        for (var node : json.getAsJsonObject("nodes").entrySet()) for (var level : node.getValue().getAsJsonObject().getAsJsonArray("levels")) {
            level.getAsJsonObject().remove("pool"); level.getAsJsonObject().remove("items");
        }
        var old = StrictJson.parse(Definition.JSON.toJson(json), Definition.class);
        assertTrue(AtlasLogic.validate(old).isEmpty()); assertEquals("general", AtlasLogic.pointPool(old, old.nodes.get("strength"), 0));
        assertTrue(old.nodes.get("strength").levels.get(0).items.isEmpty());
    }
    @Test void rankPoolOverridesCategoryAndRefundUsesActualPool() {
        var d = Definition.defaults(); d.pools.put("combat_points", new Definition.Pool());
        var n = d.nodes.get("strength"); n.levels.get(0).pool = "combat_points"; n.levels.get(0).cost = 3;
        var p = player(9); p.balance("combat_points").points = 7;
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 0)); assertEquals(4, p.balance("combat_points").points); assertEquals(9, p.balance("general").points);
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 1)); assertEquals(7, p.balance("general").points);
        n.levels.get(0).pool = "general"; n.levels.get(0).cost = 99; AtlasLogic.reset(d, p, "strength");
        assertEquals(7, p.balance("combat_points").points); assertEquals(9, p.balance("general").points);
    }
    @Test void deliveryFailureAndInsufficientPointsNeverCharge() {
        var d = Definition.defaults(); d.nodes.get("strength").levels.get(0).items.add(cost("minecraft:diamond", 2, "")); var p = player(1);
        assertEquals("insufficient_items", AtlasLogic.learn(d, p, "strength", 0, items -> false));
        assertEquals(1, p.balance("general").points); assertEquals(0, p.level("strength")); assertTrue(p.learned.isEmpty());
        p.balance("general").points = 0;
        assertEquals("insufficient", AtlasLogic.learn(d, p, "strength", 0, items -> { fail("Inventory should not be consumed"); return true; }));
    }
    @Test void staleMaxAndPrerequisitesDoNotInvokeDelivery() {
        var d = Definition.defaults(); var p = player(10); AtomicInteger calls = new AtomicInteger();
        d.nodes.get("strength").levels.get(0).items.add(cost("minecraft:diamond", 1, ""));
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 0, items -> { calls.incrementAndGet(); return true; }));
        assertEquals("stale", AtlasLogic.learn(d, p, "strength", 0, items -> { fail(); return true; }));
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 1));
        assertEquals("max_level", AtlasLogic.learn(d, p, "strength", 2, items -> { fail(); return true; }));
        d.nodes.get("miner").prerequisites.add(new Definition.Requirement("strength", 2)); AtlasLogic.reset(d, p, "strength");
        assertEquals("prerequisite", AtlasLogic.learn(d, p, "miner", 0, items -> { fail(); return true; })); assertEquals(1, calls.get());
    }
    @Test void concurrentItemRequestsConsumeExactlyOnce() throws Exception {
        var d = Definition.defaults(); var p = player(100); d.nodes.get("strength").levels.get(0).items.add(cost("minecraft:diamond", 1, ""));
        AtomicInteger inventory = new AtomicInteger(2); ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> requests = new ArrayList<>();
            for (int i = 0; i < 32; i++) requests.add(executor.submit(() -> AtlasLogic.learn(d, p, "strength", 0, items -> inventory.getAndDecrement() > 0)));
            int successes = 0; for (var request : requests) if (request.get().equals("ok")) successes++;
            assertEquals(1, successes); assertEquals(1, inventory.get()); assertEquals(99, p.balance("general").points);
        } finally { executor.shutdownNow(); }
    }
    @Test void exactNbtRequirementsGetPriorityOverWildcard() throws Exception {
        var special = new ItemStack(Items.DIAMOND, 2); special.setTag(TagParser.parseTag("{CustomModelData:7}"));
        var normal = new ItemStack(Items.DIAMOND, 3);
        List<Definition.ItemCost> costs = List.of(cost("minecraft:diamond", 3, ""), cost("minecraft:diamond", 2, "{CustomModelData:7}"));
        assertArrayEquals(new int[]{2, 3}, ItemDelivery.plan(List.of(special, normal), costs));
        assertEquals(2, special.getCount()); assertEquals(3, normal.getCount());
    }
    @Test void duplicateRequirementsCannotReuseTheSameItems() {
        var stack = new ItemStack(Items.DIAMOND, 3);
        assertNull(ItemDelivery.plan(List.of(stack), List.of(cost("minecraft:diamond", 2, ""), cost("minecraft:diamond", 2, ""))));
        assertEquals(3, stack.getCount());
    }
    @Test void itemIdAndCompleteNbtMustMatch() throws Exception {
        var stack = new ItemStack(Items.DIAMOND, 2); stack.setTag(TagParser.parseTag("{CustomModelData:7,other:1}"));
        assertNull(ItemDelivery.plan(List.of(stack), List.of(cost("minecraft:diamond", 1, "{CustomModelData:7}"))));
        assertNull(ItemDelivery.plan(List.of(stack), List.of(cost("minecraft:emerald", 1, ""))));
        assertArrayEquals(new int[]{1}, ItemDelivery.plan(List.of(stack), List.of(cost("minecraft:diamond", 1, "{other:1,CustomModelData:7}"))));
    }
    @Test void unknownItemsMalformedNbtAndInvalidPoolsAreRejected() {
        assertNotNull(ItemDelivery.validate(cost("missing:unknown", 1, ""))); assertNotNull(ItemDelivery.validate(cost("minecraft:air", 1, "")));
        assertNotNull(ItemDelivery.validate(cost("minecraft:diamond", 1, "{broken"))); assertNotNull(ItemDelivery.validate(cost("minecraft:diamond", 0, "")));
        var d = Definition.defaults(); d.nodes.get("strength").levels.get(0).pool = "missing"; assertFalse(AtlasLogic.validate(d).isEmpty());
        d.nodes.get("strength").levels.get(0).pool = ""; d.nodes.get("strength").levels.get(0).items.add(cost("minecraft:diamond", 0, "")); assertFalse(AtlasLogic.validate(d).isEmpty());
    }
    @Test void zeroPointCostStillRequiresItemsAndResetOnlyRefundsPoints() {
        var d = Definition.defaults(); var level = d.nodes.get("strength").levels.get(0); level.cost = 0; level.items.add(cost("minecraft:diamond", 1, ""));
        var p = player(0); AtomicInteger inventory = new AtomicInteger(1);
        assertEquals("insufficient_items", AtlasLogic.learn(d, p, "strength", 0));
        assertEquals("ok", AtlasLogic.learn(d, p, "strength", 0, items -> inventory.getAndDecrement() > 0));
        AtlasLogic.reset(d, p, "strength"); assertEquals(0, inventory.get()); assertEquals(0, p.balance("general").points);
    }
    @Test void copyingPreservesItemRequirementsAndIndependentPool() {
        var d = Definition.defaults(); var level = d.nodes.get("strength").levels.get(0); level.pool = "general"; level.items.add(cost("minecraft:diamond", 4, "{CustomModelData:7}"));
        var clipboard = new NodeClipboard(); clipboard.copy(d, Set.of("strength"));
        String id = clipboard.paste(d, "mining", 0, 0, false).iterator().next(); var copied = d.nodes.get(id).levels.get(0);
        assertEquals("general", copied.pool); assertEquals(4, copied.items.get(0).count); assertEquals("{CustomModelData:7}", copied.items.get(0).nbt);
        copied.items.get(0).count = 9; assertEquals(4, level.items.get(0).count);
    }
    @Test void inventoryConsumptionIncludesOffhandAndExcludesArmor() {
        var inventory = new Inventory(null); inventory.items.set(0, new ItemStack(Items.DIAMOND, 2)); inventory.offhand.set(0, new ItemStack(Items.DIAMOND, 3));
        inventory.armor.set(0, new ItemStack(Items.DIAMOND, 64)); var items = List.of(cost("minecraft:diamond", 5, ""));
        assertTrue(ItemDelivery.available(inventory, items)); assertTrue(ItemDelivery.consume(inventory, items));
        assertTrue(inventory.items.get(0).isEmpty()); assertTrue(inventory.offhand.get(0).isEmpty()); assertEquals(64, inventory.armor.get(0).getCount());
        assertFalse(ItemDelivery.consume(inventory, List.of(cost("minecraft:diamond", 1, "")))); assertEquals(64, inventory.armor.get(0).getCount());
    }
    @Test void partialInventoryShortageLeavesEverythingUntouched() {
        var inventory = new Inventory(null); inventory.items.set(0, new ItemStack(Items.DIAMOND, 3)); inventory.offhand.set(0, new ItemStack(Items.EMERALD, 1));
        var items = List.of(cost("minecraft:diamond", 2, ""), cost("minecraft:emerald", 2, ""));
        assertFalse(ItemDelivery.consume(inventory, items)); assertEquals(3, inventory.items.get(0).getCount()); assertEquals(1, inventory.offhand.get(0).getCount());
    }
}
