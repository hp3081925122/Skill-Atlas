package com.hp.skillatlas;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Set;

public final class SkillAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, SkillAtlas.MOD_ID);
    public static final DeferredHolder<Attribute, Attribute> HEALING_EFFICIENCY = ATTRIBUTES.register("healing_efficiency", () -> new RangedAttribute("attribute.name.skill_atlas.healing_efficiency", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> EXPERIENCE_GAIN = ATTRIBUTES.register("experience_gain", () -> new RangedAttribute("attribute.name.skill_atlas.experience_gain", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> LOOTING = ATTRIBUTES.register("looting", () -> new RangedAttribute("attribute.name.skill_atlas.looting", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> FORTUNE = ATTRIBUTES.register("fortune", () -> new RangedAttribute("attribute.name.skill_atlas.fortune", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> MINING_SPEED = ATTRIBUTES.register("mining_speed", () -> new RangedAttribute("attribute.name.skill_atlas.mining_speed", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> STONE_MINERAL_CHANCE = ATTRIBUTES.register("stone_mineral_chance", () -> new RangedAttribute("attribute.name.skill_atlas.stone_mineral_chance", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> MINING_EXPERIENCE = ATTRIBUTES.register("mining_experience", () -> new RangedAttribute("attribute.name.skill_atlas.mining_experience", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> HARVEST_EXPERIENCE = ATTRIBUTES.register("harvest_experience", () -> new RangedAttribute("attribute.name.skill_atlas.harvest_experience", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> FISHING_LURE = ATTRIBUTES.register("fishing_lure", () -> new RangedAttribute("attribute.name.skill_atlas.fishing_lure", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> FISHING_LUCK = ATTRIBUTES.register("fishing_luck", () -> new RangedAttribute("attribute.name.skill_atlas.fishing_luck", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> ENCHANTING_LEVEL_CHANCE = ATTRIBUTES.register("enchanting_cap", () -> new RangedAttribute("attribute.name.skill_atlas.enchanting_cap", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> JUMP_HEIGHT = ATTRIBUTES.register("jump_height", () -> new RangedAttribute("attribute.name.skill_atlas.jump_height", 0.0D, 0.0D, 16.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> FALL_DAMAGE_REDUCTION = ATTRIBUTES.register("fall_damage_reduction", () -> new RangedAttribute("attribute.name.skill_atlas.fall_damage_reduction", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> SMELTING_SPEED = ATTRIBUTES.register("smelting_speed", () -> new RangedAttribute("attribute.name.skill_atlas.smelting_speed", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> RAW_ORE_DOUBLE_CHANCE = ATTRIBUTES.register("raw_ore_double_chance", () -> new RangedAttribute("attribute.name.skill_atlas.raw_ore_double_chance", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> FUEL_EFFICIENCY = ATTRIBUTES.register("fuel_efficiency", () -> new RangedAttribute("attribute.name.skill_atlas.fuel_efficiency", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> SMELTING_EXPERIENCE = ATTRIBUTES.register("smelting_experience", () -> new RangedAttribute("attribute.name.skill_atlas.smelting_experience", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> LAPIS_SAVING = ATTRIBUTES.register("lapis_saving", () -> new RangedAttribute("attribute.name.skill_atlas.lapis_saving", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> ENCHANTING_REFUND = ATTRIBUTES.register("enchanting_refund", () -> new RangedAttribute("attribute.name.skill_atlas.enchanting_refund", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> BOOK_COPY = ATTRIBUTES.register("book_copy", () -> new RangedAttribute("attribute.name.skill_atlas.book_copy", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> ENCHANTING_INSIGHT = ATTRIBUTES.register("enchanting_insight", () -> new RangedAttribute("attribute.name.skill_atlas.enchanting_insight", 0.0D, 0.0D, 1.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> ELYTRA_SPEED = ATTRIBUTES.register("elytra_speed", () -> new RangedAttribute("attribute.name.skill_atlas.elytra_speed", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> CROP_REPLANTING = ATTRIBUTES.register("crop_replanting", () -> new RangedAttribute("attribute.name.skill_atlas.crop_replanting", 0.0D, 0.0D, 1.0D).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> HARVEST_YIELD = ATTRIBUTES.register("harvest_yield", () -> new RangedAttribute("attribute.name.skill_atlas.harvest_yield", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    private static final Set<String> PERCENT = Set.of("skill_atlas:healing_efficiency", "skill_atlas:experience_gain", "skill_atlas:mining_speed", "skill_atlas:stone_mineral_chance", "skill_atlas:mining_experience", "skill_atlas:enchanting_cap", "skill_atlas:fall_damage_reduction", "skill_atlas:smelting_speed", "skill_atlas:raw_ore_double_chance", "skill_atlas:fuel_efficiency", "skill_atlas:smelting_experience", "skill_atlas:lapis_saving", "skill_atlas:enchanting_refund", "skill_atlas:book_copy", "skill_atlas:elytra_speed", "skill_atlas:harvest_yield");

    private SkillAttributes() {
    }

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
        bus.addListener(SkillAttributes::onAttributes);
    }

    private static void onAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, HEALING_EFFICIENCY);
        event.add(EntityType.PLAYER, EXPERIENCE_GAIN);
        event.add(EntityType.PLAYER, LOOTING);
        event.add(EntityType.PLAYER, FORTUNE);
        event.add(EntityType.PLAYER, MINING_SPEED);
        event.add(EntityType.PLAYER, STONE_MINERAL_CHANCE);
        event.add(EntityType.PLAYER, MINING_EXPERIENCE);
        event.add(EntityType.PLAYER, HARVEST_EXPERIENCE);
        event.add(EntityType.PLAYER, FISHING_LURE);
        event.add(EntityType.PLAYER, FISHING_LUCK);
        event.add(EntityType.PLAYER, ENCHANTING_LEVEL_CHANCE);
        event.add(EntityType.PLAYER, JUMP_HEIGHT);
        event.add(EntityType.PLAYER, FALL_DAMAGE_REDUCTION);
        event.add(EntityType.PLAYER, SMELTING_SPEED);
        event.add(EntityType.PLAYER, RAW_ORE_DOUBLE_CHANCE);
        event.add(EntityType.PLAYER, FUEL_EFFICIENCY);
        event.add(EntityType.PLAYER, SMELTING_EXPERIENCE);
        event.add(EntityType.PLAYER, LAPIS_SAVING);
        event.add(EntityType.PLAYER, ENCHANTING_REFUND);
        event.add(EntityType.PLAYER, BOOK_COPY);
        event.add(EntityType.PLAYER, ENCHANTING_INSIGHT);
        event.add(EntityType.PLAYER, ELYTRA_SPEED);
        event.add(EntityType.PLAYER, CROP_REPLANTING);
        event.add(EntityType.PLAYER, HARVEST_YIELD);
    }

    public static boolean percent(String id) {
        return PERCENT.contains(id);
    }

    public static double value(LivingEntity entity, net.minecraft.core.Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return 0.0D;
        }
        return instance.getValue();
    }

    public static int roll(double value, RandomSource random) {
        int whole = (int) Math.floor(value);
        if (random.nextDouble() < value - whole) {
            whole++;
        }
        return whole;
    }
}
