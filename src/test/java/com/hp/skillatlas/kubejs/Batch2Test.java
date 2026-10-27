package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.data.*;
import com.hp.skillatlas.kubejs.network.NavigationPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Batch2Test {
    private AtlasDefinition base() {
        AtlasDefinition d = new AtlasDefinition();
        d.pools.put("training", new PointPool("training", "Training", 0x55ffff, 0));
        d.categories.put("training", new Category("training", "Training", Icon.item("minecraft:book"), "training")); return d;
    }
    private TypeRegistry registry(String unit) {
        AcquisitionType type = new AcquisitionType().name("Machine").unit(unit).validate(data -> data.get("machine") instanceof String); type.close();
        return new TypeRegistry(Map.of(), Map.of(), Map.of("pack:machine", type));
    }
    @Test void customAcquisitionSurvivesDefinitionRoundTripAndCopy() {
        DefinitionsEvent e = new DefinitionsEvent(base()); e.modifyPool("training", p -> p.customAcquisition("pack:machine", Map.of("machine", "press"), 8, 2, 10));
        AtlasDefinition d = e.finish(registry("count")); var json = d.toJson(); ExtraData.write(d, json);
        AtlasDefinition decoded = AtlasDefinition.fromJson(json); ExtraData.read(json, decoded); ExtraData.validate(decoded, registry("count"));
        assertEquals(Acquisitions.binding(d, "training"), Acquisitions.binding(decoded, "training"));
        DefinitionsEvent copy = new DefinitionsEvent(d); copy.removePool("training"); copy.removeCategory("training"); copy.finish(registry("count"));
        assertNotNull(Acquisitions.binding(d, "training"));
    }
    @Test void customUnitsNeverInheritMovementOrPlaytimeMultipliers() {
        for (String unit : List.of("count", "seconds", "centimeters")) {
            DefinitionsEvent e = new DefinitionsEvent(base()); e.modifyPool("training", p -> p.customAcquisition("pack:machine", Map.of("machine", "press"), 8, 1, 0));
            AtlasDefinition d = e.finish(registry(unit)); assertEquals(8, d.pools.get("training").counterTarget()); assertEquals(unit, Acquisitions.binding(d, "training").get("unit").getAsString());
        }
    }
    @Test void invalidOrUnknownAcquisitionCannotReplaceDefinition() {
        DefinitionsEvent e = new DefinitionsEvent(base()); e.modifyPool("training", p -> p.customAcquisition("pack:machine", Map.of("machine", 2), 8, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> e.finish(registry("count")));
        DefinitionsEvent unknown = new DefinitionsEvent(base()); unknown.modifyPool("training", p -> p.customAcquisition("pack:missing", Map.of(), 8, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> unknown.finish(registry("count")));
    }
    @Test void nativeAcquisitionSwitchClearsExternalBinding() {
        DefinitionsEvent e = new DefinitionsEvent(base()); e.modifyPool("training", p -> p.customAcquisition("pack:machine", Map.of("machine", "press"), 8, 1, 0).acquisition("fish", 4, 1, 0));
        AtlasDefinition d = e.finish(registry("count")); assertNull(Acquisitions.binding(d, "training")); assertEquals(RuleType.FISH, d.pools.get("training").method);
    }
    @Test void acquisitionTypesRequireNamesAndFreezeAfterRegistration() {
        AcquisitionTypesEvent e = new AcquisitionTypesEvent(); AcquisitionType[] saved = new AcquisitionType[1];
        e.register("pack:machine", t -> { saved[0] = t; t.name("Machine"); }); e.finish();
        assertThrows(IllegalStateException.class, () -> saved[0].unit("seconds"));
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionTypesEvent().register("pack:empty", t -> {}));
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionType().unit("ticks"));
    }
    @Test void navigationCodecBoundsAndPreservesIdentity() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try { NavigationPacket p = new NavigationPacket(1, "haste", 72); NavigationPacket.encode(p, buf); assertEquals(p, NavigationPacket.decode(buf)); }
        finally { buf.release(); }
        FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        try { bad.writeByte(2); assertThrows(IllegalArgumentException.class, () -> NavigationPacket.decode(bad)); }
        finally { bad.release(); }
    }
    @Test void navigationRejectsUnknownOrHiddenTargetsWithoutChangingProgress() {
        DefinitionsEvent e = new DefinitionsEvent(base()); e.createNode("gate").category("training").level(1, rank -> {});
        e.createNode("hidden").category("training").hidden(true).requires("gate").level(1, rank -> {});
        AtlasDefinition d = e.finish(registry("count")); PlayerProgress p = new PlayerProgress();
        assertEquals("node_hidden", Navigation.validate(d, p, 1, "hidden").code);
        assertEquals("unknown_node", Navigation.validate(d, p, 1, "missing").code);
        assertEquals("unknown_category", Navigation.validate(d, p, 0, "missing").code);
        assertTrue(Navigation.validate(d, p, 0, "training").ok); assertTrue(p.levels().isEmpty());
    }
    @Test void tooltipAddsCopiesAndEnforcesClosedScopeAndLimits() {
        var base = Component.literal("Original"); var line = Component.literal("Extra");
        TooltipEvent event = new TooltipEvent(null, "haste", "training", 1, 3, 5, true, List.of(base));
        event.add(line); line.append(" changed"); assertEquals("Extra", event.finish().get(0).getString());
        assertThrows(IllegalStateException.class, () -> event.add(base));
        event.getBaseLines().get(0).getSiblings().clear(); assertEquals("Original", base.getString());
        TooltipEvent capped = new TooltipEvent(null, "haste", "training", 1, 3, 5, true, List.of());
        for (int i = 0; i < 32; i++) capped.add(Component.literal("x"));
        assertThrows(IllegalArgumentException.class, () -> capped.add(Component.literal("x")));
    }
    @Test void caughtCustomConfigurationFailurePreservesPreviousAcquisition() {
        DefinitionsEvent e = new DefinitionsEvent(base());
        e.modifyPool("training", p -> {
            assertThrows(IllegalArgumentException.class, () -> p.customAcquisition("missing_namespace", Map.of(), 8, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> p.customAcquisition("pack:machine", Map.of("payload", "x".repeat(17000)), 8, 1, 0));
        });
        AtlasDefinition d = e.finish(registry("count")); assertEquals(RuleType.KILL, d.pools.get("training").method); assertNull(Acquisitions.binding(d, "training"));
    }
    @Test void caughtIncompleteTypeCannotBecomeACommittedRegistry() {
        AcquisitionTypesEvent a = new AcquisitionTypesEvent(); assertThrows(IllegalArgumentException.class, () -> a.register("pack:missing", t -> {})); assertThrows(IllegalArgumentException.class, a::finish);
        ConditionTypesEvent c = new ConditionTypesEvent(); assertThrows(IllegalArgumentException.class, () -> c.register("pack:missing", t -> {})); assertThrows(IllegalArgumentException.class, c::finish);
        RewardTypesEvent r = new RewardTypesEvent(); assertThrows(IllegalArgumentException.class, () -> r.register("pack:missing", t -> {})); assertThrows(IllegalArgumentException.class, r::finish);
    }
    @Test void acquisitionDisplaySupportsLiteralTextFallback() {
        assertEquals("Machine", Component.translatable("Machine").getString());
        assertEquals("完成机器加工", Component.translatable("完成机器加工").getString());
    }
}
