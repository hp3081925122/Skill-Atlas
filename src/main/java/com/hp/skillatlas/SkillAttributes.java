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
    private static final Set<String> PERCENT = Set.of("skill_atlas:healing_efficiency", "skill_atlas:experience_gain", "skill_atlas:mining_speed");

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
