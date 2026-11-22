package com.hp.skillatlas.kubejs;

import com.google.gson.JsonObject;
import com.hp.skillatlas.data.*;
import com.hp.skillatlas.kubejs.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() throws Exception {
        if (net.neoforged.fml.loading.FMLLoader.getCurrentOrNull() == null) {
            java.lang.reflect.Constructor<net.neoforged.fml.loading.FMLLoader> create = net.neoforged.fml.loading.FMLLoader.class.getDeclaredConstructor(ClassLoader.class, String[].class, net.neoforged.api.distmarker.Dist.class, boolean.class, java.nio.file.Path.class);
            create.setAccessible(true);
            net.neoforged.fml.loading.FMLLoader loader = create.newInstance(ExtensionsTest.class.getClassLoader(), new String[0], net.neoforged.api.distmarker.Dist.DEDICATED_SERVER, false, java.nio.file.Files.createTempDirectory("skill-atlas-test"));
            java.lang.reflect.Field mods = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("loadingModList");
            mods.setAccessible(true);
            mods.set(loader, net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()));
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private AtlasDefinition base() {
        AtlasDefinition d = new AtlasDefinition();
        d.pools.put("training", new PointPool("training", "Training", 0x55ffff, 0));
        d.categories.put("training", new Category("training", "Training", Icon.item("minecraft:book"), "training"));
        return d;
    }
    private TypeRegistry types() {
        ConditionType condition = new ConditionType().validate(data -> data.get("stage") instanceof String).test((ctx, data) -> true).describe((ctx, data) -> Component.literal("Gate")); condition.close();
        RewardType reward = new RewardType().describe((ctx, data) -> Component.literal("Reward")).onLearn((ctx, data) -> {}); reward.close();
        return new TypeRegistry(Map.of("pack:gate", condition), Map.of("pack:reward", reward));
    }
    @Test void customDataRoundTripsWithoutPersistingCallbacks() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        e.createNode("study").category("training").condition("pack:gate", Map.of("stage", "start"))
                .level(1, rank -> rank.condition("pack:gate", Map.of("stage", "end")).reward(Map.of("type", "pack:reward", "data", Map.of("amount", 2))));
        AtlasDefinition result = e.finish(types()); JsonObject json = result.toJson(); ExtraData.write(result, json);
        AtlasDefinition read = AtlasDefinition.fromJson(json); ExtraData.read(json, read); ExtraData.validate(read, types());
        assertEquals(2, ExtraData.entries(read, "study", 1, "conditions", true).size());
        assertEquals(1, ExtraData.entries(read, "study", 1, "rewards", false).size());
        assertFalse(json.toString().contains("callback"));
    }
    @Test void copyAndSnapshotsDoNotShareMutableScriptData() {
        DefinitionsEvent e = new DefinitionsEvent(base()); e.createNode("study").category("training").condition("pack:gate", Map.of("stage", "start")).level(1, r -> {});
        AtlasDefinition first = e.finish(types()); DefinitionsEvent second = new DefinitionsEvent(first);
        second.modifyNode("study", n -> n.clearConditions()); AtlasDefinition next = second.finish(types());
        assertEquals(1, ExtraData.entries(first, "study", 1, "conditions", true).size());
        assertTrue(ExtraData.entries(next, "study", 1, "conditions", true).isEmpty());
        Map<?, ?> data = TypeRegistry.data(ExtraData.entries(first, "study", 1, "conditions", true).get(0).getAsJsonObject("data"));
        assertThrows(UnsupportedOperationException.class, data::clear);
    }
    @Test void unknownAndInvalidTypeDataCannotCommit() {
        DefinitionsEvent unknown = new DefinitionsEvent(base()); unknown.createNode("study").category("training").condition("pack:missing", Map.of()).level(1, r -> {});
        assertThrows(IllegalArgumentException.class, () -> unknown.finish(types()));
        DefinitionsEvent invalid = new DefinitionsEvent(base()); invalid.createNode("study").category("training").condition("pack:gate", Map.of("stage", 3)).level(1, r -> {});
        assertThrows(IllegalArgumentException.class, () -> invalid.finish(types()));
    }
    @Test void unknownNodesAndRanksAndOversizedPayloadsAreRejected() {
        AtlasDefinition d = base(); ExtraData.node(d, "missing");
        assertThrows(IllegalArgumentException.class, () -> ExtraData.validate(d, types()));
        DefinitionsEvent e = new DefinitionsEvent(base()); e.createNode("study").category("training").level(1, r -> {});
        ExtraData.append(ExtraData.rank(e.draft(), "study", 2), "rewards", "pack:reward", Map.of());
        assertThrows(IllegalArgumentException.class, () -> e.finish(types()));
        assertThrows(IllegalArgumentException.class, () -> ExtraData.append(new JsonObject(), "conditions", "pack:gate", Map.of("stage", "x".repeat(16385))));
    }
    @Test void typeRegistrationRequiresCompleteReversibleContractsAndClosesBuilders() {
        ConditionTypesEvent event = new ConditionTypesEvent(); ConditionType[] saved = new ConditionType[1];
        event.register("pack:gate", type -> { saved[0] = type; type.test((ctx, data) -> true).describe((ctx, data) -> Component.empty()); });
        assertThrows(IllegalStateException.class, () -> saved[0].test((ctx, data) -> false));
        assertThrows(IllegalArgumentException.class, () -> event.register("pack:gate", type -> {}));
        event.finish(); assertThrows(IllegalStateException.class, () -> event.register("pack:late", type -> {}));
        assertThrows(IllegalArgumentException.class, () -> new RewardType().describe((ctx, data) -> Component.empty()).onActivate((ctx, data) -> {}).close());
        assertThrows(IllegalArgumentException.class, () -> ExtraData.typeId("plain"));
    }
    @Test void queryBarrierAlwaysReleasesAfterErrorsAndSupportsNestedReads() {
        assertFalse(TypeRegistry.evaluating());
        assertThrows(IllegalStateException.class, () -> TypeRegistry.evaluate(() -> { assertTrue(TypeRegistry.evaluating()); return TypeRegistry.evaluate(() -> { throw new IllegalStateException("expected"); }); }));
        assertFalse(TypeRegistry.evaluating());
    }
    @Test void requestCodecPreservesBoundedQuoteIdentity() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            QuoteRequest input = new QuoteRequest(true, "study", UUID.randomUUID().toString()); QuoteRequest.encode(input, buf);
            assertEquals(input, QuoteRequest.decode(buf));
        } finally { buf.release(); }
    }
    @Test void previewCodecPreservesComponentStylesAndAllCostData() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            JsonObject cost = new SkillLevel().toJson();
            Component styled = Component.literal("Gate").withStyle(s -> s.withColor(0x123456).withBold(true));
            LearningQuote input = new LearningQuote("study", 5, 0, UUID.randomUUID().toString(), 100, false, "condition_failed", styled, cost,
                    List.of(new LearningQuote.ConditionLine(false, styled)), List.of(styled), List.of(styled));
            QuoteResponse.encode(new QuoteResponse(input), buf); LearningQuote output = QuoteResponse.decode(buf).quote();
            assertEquals(input, output); assertEquals(0x123456, output.conditions().get(0).description().getStyle().getColor().getValue());
        } finally { buf.release(); }
    }
    @Test void stableRewardKeysSeparateRankAndSlotAndDoNotDependOnPlayerIdentity() {
        RewardContext a = new RewardContext(null, "study", 1, 0, "learn"), b = new RewardContext(null, "study", 1, 0, "login");
        assertEquals(a.modifierId, b.modifierId);
        assertNotEquals(a.modifierId, new RewardContext(null, "study", 2, 0, "learn").modifierId);
        assertNotEquals(a.modifierId, new RewardContext(null, "study", 1, 1, "learn").modifierId);
    }
    @Test void scriptDataRejectsFunctionsCyclesAndNonFiniteNumbers() {
        Map<String, Object> cyclic = new HashMap<>(); cyclic.put("self", cyclic);
        assertThrows(IllegalArgumentException.class, () -> Values.plain(cyclic));
        assertThrows(IllegalArgumentException.class, () -> Values.plain(Map.of("function", (Runnable)() -> {})));
        assertThrows(IllegalArgumentException.class, () -> Values.plain(Map.of("value", Double.NaN)));
        assertEquals(Values.json(Map.of("array", List.of(1, 2, 3))), Values.plain(Map.of("array", List.of(1, 2, 3))));
    }
    @Test void quoteCodecPreservesNativeMaximumDeliveryAndCommandSizes() {
        SkillLevel rank = new SkillLevel();
        for (int i = 0; i < SkillLevel.MAX_ITEMS; i++) rank.items.add(new ItemCost("minecraft:diamond", 1, "{text:\"" + "\u2028".repeat(ItemCost.MAX_NBT - 9) + "\"}"));
        for (int i = 0; i < SkillLevel.MAX_REWARDS; i++) {
            JsonObject reward = new JsonObject(); reward.addProperty("type", "command"); reward.addProperty("command", "x" + "\u2028".repeat(1023)); rank.rewards.add(Reward.fromJson(reward));
        }
        LearningQuote input = new LearningQuote("study", 1, 0, UUID.randomUUID().toString(), 100, true, "ok", Component.empty(), rank.toJson(), List.of(), List.of(), List.of());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try { QuoteResponse.encode(new QuoteResponse(input), buffer); assertEquals(input, QuoteResponse.decode(buffer).quote()); }
        finally { buffer.release(); }
    }
    @Test void deliveryDataNormalizesRhinoConcatenatedStrings() {
        CharSequence value = dev.latvian.mods.rhino.ConsString.flatten("{Payload:\"", dev.latvian.mods.rhino.ConsString.flatten("x".repeat(8000), "\"}"));
        JsonObject delivery = Values.plain(Map.of("item", "minecraft:diamond", "count", 1, "nbt", value)).getAsJsonObject();
        assertTrue(delivery.get("nbt").isJsonPrimitive());
        assertEquals(value.toString(), delivery.get("nbt").getAsString());
    }
}
