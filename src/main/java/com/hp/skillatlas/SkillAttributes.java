package com.hp.skillatlas;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public final class SkillAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, SkillAtlas.MOD_ID);
    public static final RegistryObject<Attribute> HEALING_EFFICIENCY = ATTRIBUTES.register("healing_efficiency", () -> new RangedAttribute("attribute.name.skill_atlas.healing_efficiency", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> EXPERIENCE_GAIN = ATTRIBUTES.register("experience_gain", () -> new RangedAttribute("attribute.name.skill_atlas.experience_gain", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> LOOTING = ATTRIBUTES.register("looting", () -> new RangedAttribute("attribute.name.skill_atlas.looting", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> FORTUNE = ATTRIBUTES.register("fortune", () -> new RangedAttribute("attribute.name.skill_atlas.fortune", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> MINING_SPEED = ATTRIBUTES.register("mining_speed", () -> new RangedAttribute("attribute.name.skill_atlas.mining_speed", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> STONE_MINERAL_CHANCE = ATTRIBUTES.register("stone_mineral_chance", () -> new RangedAttribute("attribute.name.skill_atlas.stone_mineral_chance", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> MINING_EXPERIENCE = ATTRIBUTES.register("mining_experience", () -> new RangedAttribute("attribute.name.skill_atlas.mining_experience", 0.0D, -100.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> HARVEST_EXPERIENCE = ATTRIBUTES.register("harvest_experience", () -> new RangedAttribute("attribute.name.skill_atlas.harvest_experience", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> FISHING_LURE = ATTRIBUTES.register("fishing_lure", () -> new RangedAttribute("attribute.name.skill_atlas.fishing_lure", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> FISHING_LUCK = ATTRIBUTES.register("fishing_luck", () -> new RangedAttribute("attribute.name.skill_atlas.fishing_luck", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> ENCHANTING_LEVEL_CHANCE = ATTRIBUTES.register("enchanting_cap", () -> new RangedAttribute("attribute.name.skill_atlas.enchanting_cap", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> JUMP_HEIGHT = ATTRIBUTES.register("jump_height", () -> new RangedAttribute("attribute.name.skill_atlas.jump_height", 0.0D, 0.0D, 16.0D).setSyncable(true));
    public static final RegistryObject<Attribute> FALL_DAMAGE_REDUCTION = ATTRIBUTES.register("fall_damage_reduction", () -> new RangedAttribute("attribute.name.skill_atlas.fall_damage_reduction", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> SMELTING_SPEED = ATTRIBUTES.register("smelting_speed", () -> new RangedAttribute("attribute.name.skill_atlas.smelting_speed", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> RAW_ORE_DOUBLE_CHANCE = ATTRIBUTES.register("raw_ore_double_chance", () -> new RangedAttribute("attribute.name.skill_atlas.raw_ore_double_chance", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> FUEL_EFFICIENCY = ATTRIBUTES.register("fuel_efficiency", () -> new RangedAttribute("attribute.name.skill_atlas.fuel_efficiency", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> SMELTING_EXPERIENCE = ATTRIBUTES.register("smelting_experience", () -> new RangedAttribute("attribute.name.skill_atlas.smelting_experience", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> LAPIS_SAVING = ATTRIBUTES.register("lapis_saving", () -> new RangedAttribute("attribute.name.skill_atlas.lapis_saving", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> ENCHANTING_REFUND = ATTRIBUTES.register("enchanting_refund", () -> new RangedAttribute("attribute.name.skill_atlas.enchanting_refund", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> BOOK_COPY = ATTRIBUTES.register("book_copy", () -> new RangedAttribute("attribute.name.skill_atlas.book_copy", 0.0D, 0.0D, 100.0D).setSyncable(true));
    public static final RegistryObject<Attribute> ENCHANTING_INSIGHT = ATTRIBUTES.register("enchanting_insight", () -> new RangedAttribute("attribute.name.skill_atlas.enchanting_insight", 0.0D, 0.0D, 1.0D).setSyncable(true));
    public static final RegistryObject<Attribute> ELYTRA_SPEED = ATTRIBUTES.register("elytra_speed", () -> new RangedAttribute("attribute.name.skill_atlas.elytra_speed", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    public static final RegistryObject<Attribute> CROP_REPLANTING = ATTRIBUTES.register("crop_replanting", () -> new RangedAttribute("attribute.name.skill_atlas.crop_replanting", 0.0D, 0.0D, 1.0D).setSyncable(true));
    public static final RegistryObject<Attribute> HARVEST_YIELD = ATTRIBUTES.register("harvest_yield", () -> new RangedAttribute("attribute.name.skill_atlas.harvest_yield", 0.0D, 0.0D, 10_000.0D).setSyncable(true));
    private static final Set<String> PERCENT = Set.of("skill_atlas:healing_efficiency", "skill_atlas:experience_gain", "skill_atlas:mining_speed", "skill_atlas:stone_mineral_chance", "skill_atlas:mining_experience", "skill_atlas:enchanting_cap", "skill_atlas:fall_damage_reduction", "skill_atlas:smelting_speed", "skill_atlas:raw_ore_double_chance", "skill_atlas:fuel_efficiency", "skill_atlas:smelting_experience", "skill_atlas:lapis_saving", "skill_atlas:enchanting_refund", "skill_atlas:book_copy", "skill_atlas:elytra_speed", "skill_atlas:harvest_yield");

    private SkillAttributes() {
    }

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
        bus.addListener(SkillAttributes::onAttributes);
    }

    private static void onAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, HEALING_EFFICIENCY.get());
        event.add(EntityType.PLAYER, EXPERIENCE_GAIN.get());
        event.add(EntityType.PLAYER, LOOTING.get());
        event.add(EntityType.PLAYER, FORTUNE.get());
        event.add(EntityType.PLAYER, MINING_SPEED.get());
        event.add(EntityType.PLAYER, STONE_MINERAL_CHANCE.get());
        event.add(EntityType.PLAYER, MINING_EXPERIENCE.get());
        event.add(EntityType.PLAYER, HARVEST_EXPERIENCE.get());
        event.add(EntityType.PLAYER, FISHING_LURE.get());
        event.add(EntityType.PLAYER, FISHING_LUCK.get());
        event.add(EntityType.PLAYER, ENCHANTING_LEVEL_CHANCE.get());
        event.add(EntityType.PLAYER, JUMP_HEIGHT.get());
        event.add(EntityType.PLAYER, FALL_DAMAGE_REDUCTION.get());
        event.add(EntityType.PLAYER, SMELTING_SPEED.get());
        event.add(EntityType.PLAYER, RAW_ORE_DOUBLE_CHANCE.get());
        event.add(EntityType.PLAYER, FUEL_EFFICIENCY.get());
        event.add(EntityType.PLAYER, SMELTING_EXPERIENCE.get());
        event.add(EntityType.PLAYER, LAPIS_SAVING.get());
        event.add(EntityType.PLAYER, ENCHANTING_REFUND.get());
        event.add(EntityType.PLAYER, BOOK_COPY.get());
        event.add(EntityType.PLAYER, ENCHANTING_INSIGHT.get());
        event.add(EntityType.PLAYER, ELYTRA_SPEED.get());
        event.add(EntityType.PLAYER, CROP_REPLANTING.get());
        event.add(EntityType.PLAYER, HARVEST_YIELD.get());
    }

    public static boolean percent(String id) {
        return PERCENT.contains(id);
    }

    public static double value(LivingEntity entity, RegistryObject<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute.get());
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
