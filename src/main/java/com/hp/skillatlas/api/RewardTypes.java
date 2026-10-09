package com.hp.skillatlas.api;

import com.hp.skillatlas.model.Definition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class RewardTypes {
    private static final Map<String, RewardType> TYPES = new LinkedHashMap<>();
    static {
        register("attribute", new RewardType() {
            public List<String> validate(Definition.Reward r) { var id = ResourceLocation.tryParse(r.target); return id != null && ForgeRegistries.ATTRIBUTES.containsKey(id) ? List.of() : List.of("Unknown attribute: " + r.target); }
            public void apply(ServerPlayer player, Definition.Reward r, UUID key, RewardContext context) {
                var attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.parse(r.target));
                var instance = attribute == null ? null : player.getAttribute(attribute); if (instance == null) return;
                var operation = switch (r.operation) { case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE; case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL; default -> AttributeModifier.Operation.ADDITION; };
                instance.removeModifier(key); instance.addTransientModifier(new AttributeModifier(key, "skill_atlas:" + key, r.amount, operation));
            }
            public void revoke(ServerPlayer player, Definition.Reward r, UUID key) {
                var attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.parse(r.target));
                var instance = attribute == null ? null : player.getAttribute(attribute); if (instance != null) instance.removeModifier(key);
            }
        });
        register("effect", new RewardType() {
            public List<String> validate(Definition.Reward r) { return effect(r.target) ? List.of() : List.of("Unknown effect: " + r.target); }
            public void apply(ServerPlayer player, Definition.Reward r, UUID key, RewardContext context) { context.periodic(r); }
        });
        register("immunity", new RewardType() {
            public List<String> validate(Definition.Reward r) { return r.effects.stream().filter(key -> !effect(key)).map(key -> "Unknown immunity effect: " + key).toList(); }
            public void apply(ServerPlayer player, Definition.Reward r, UUID key, RewardContext context) { r.effects.forEach(id -> context.immune(ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.parse(id)))); }
        });
    }
    public static void register(String id, RewardType type) { if (TYPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(type)) != null) throw new IllegalArgumentException("Duplicate reward type: " + id); }
    public static RewardType get(String id) { return TYPES.get(id); }
    public static Set<String> ids() { return Collections.unmodifiableSet(TYPES.keySet()); }
    private static boolean effect(String id) { var key = ResourceLocation.tryParse(id); return key != null && ForgeRegistries.MOB_EFFECTS.containsKey(key); }
    private RewardTypes() {}
}
