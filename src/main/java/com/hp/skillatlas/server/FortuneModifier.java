package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

public final class FortuneModifier extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SkillAtlas.MOD_ID);
    public static final RegistryObject<Codec<FortuneModifier>> CODEC = SERIALIZERS.register("fortune", () -> RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, FortuneModifier::new)));
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    public FortuneModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (ACTIVE.get()) {
            return loot;
        }
        BlockState state = ctx.getParamOrNull(LootContextParams.BLOCK_STATE);
        ItemStack tool = ctx.getParamOrNull(LootContextParams.TOOL);
        Entity entity = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        if (state == null || tool == null || origin == null || !(entity instanceof Player player)) {
            return loot;
        }
        if (!state.getBlock().getLootTable().equals(ctx.getQueriedLootTableId())) {
            return loot;
        }
        double value = SkillAttributes.value(player, SkillAttributes.FORTUNE);
        if (value <= 0.0D || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return loot;
        }
        int bonus = SkillAttributes.roll(value, player.getRandom());
        if (bonus <= 0) {
            return loot;
        }
        ItemStack boosted = tool.copy();
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(boosted);
        int base = enchantments.getOrDefault(Enchantments.BLOCK_FORTUNE, 0);
        enchantments.put(Enchantments.BLOCK_FORTUNE, base + bonus);
        EnchantmentHelper.setEnchantments(enchantments, boosted);
        LootParams params = new LootParams.Builder(ctx.getLevel())
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, boosted)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, ctx.getParamOrNull(LootContextParams.BLOCK_ENTITY))
                .withOptionalParameter(LootContextParams.EXPLOSION_RADIUS, ctx.getParamOrNull(LootContextParams.EXPLOSION_RADIUS))
                .create(LootContextParamSets.BLOCK);
        LootTable table = ctx.getLevel().getServer().getLootData().getLootTable(ctx.getQueriedLootTableId());
        ACTIVE.set(true);
        try {
            ObjectArrayList<ItemStack> rerolled = table.getRandomItems(params);
            SkillAtlas.LOGGER.debug("Fortune attribute rerolled {} with fortune {} -> {}: {} stacks", ctx.getQueriedLootTableId(), base, base + bonus, rerolled.size());
            return rerolled;
        } finally {
            ACTIVE.set(false);
        }
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
