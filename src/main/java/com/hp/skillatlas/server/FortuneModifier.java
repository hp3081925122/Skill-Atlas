package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import com.mojang.serialization.MapCodec;
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
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Map;

public final class FortuneModifier extends LootModifier {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SkillAtlas.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<FortuneModifier>> CODEC = SERIALIZERS.register("fortune", () -> RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, FortuneModifier::new)));
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<StoneMineralsModifier>> STONE_MINERALS = SERIALIZERS.register("stone_minerals", () -> StoneMineralsModifier.CODEC);
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    public FortuneModifier(LootItemCondition[] conditions, int priority) {
        super(conditions, priority);
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    public static boolean isRerolling() {
        return ACTIVE.get();
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (ACTIVE.get()) {
            return loot;
        }
        BlockState state = ctx.getOptionalParameter(LootContextParams.BLOCK_STATE);
        ItemInstance tool = ctx.getOptionalParameter(LootContextParams.TOOL);
        Entity entity = ctx.getOptionalParameter(LootContextParams.THIS_ENTITY);
        Vec3 origin = ctx.getOptionalParameter(LootContextParams.ORIGIN);
        if (state == null || tool == null || origin == null || !(entity instanceof Player player)) {
            return loot;
        }
        if (state.getBlock().getLootTable().map(ResourceKey::identifier).filter(ctx.getQueriedLootTableId()::equals).isEmpty()) {
            return loot;
        }
        double value = SkillAttributes.value(player, SkillAttributes.FORTUNE);
        var registry = ctx.getResolver().lookupOrThrow(Registries.ENCHANTMENT);
        if (value <= 0.0D || EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.SILK_TOUCH), tool) > 0) {
            return loot;
        }
        int bonus = SkillAttributes.roll(value, player.getRandom());
        if (bonus <= 0) {
            return loot;
        }
        ItemStack boosted = tool instanceof ItemStack stack ? stack.copy()
                : tool instanceof ItemStackTemplate template ? template.create() : ItemStack.EMPTY;
        if (boosted.isEmpty()) {
            return loot;
        }
        var fortune = registry.getOrThrow(Enchantments.FORTUNE);
        int base = EnchantmentHelper.getItemEnchantmentLevel(fortune, tool);
        EnchantmentHelper.updateEnchantments(boosted, enchantments -> enchantments.set(fortune, base + bonus));
        LootParams params = new LootParams.Builder(ctx.getLevel())
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, boosted)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, ctx.getOptionalParameter(LootContextParams.BLOCK_ENTITY))
                .withOptionalParameter(LootContextParams.EXPLOSION_RADIUS, ctx.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS))
                .create(LootContextParamSets.BLOCK);
        LootTable table = ctx.getLevel().getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable().orElseThrow());
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
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
