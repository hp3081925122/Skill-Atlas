package com.hp.skillatlas.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;

public interface EnchantmentMenuState {
    List<EnchantmentInstance> skillAtlas$options(ItemStack stack, int slot, int cost);
    CompoundTag skillAtlas$preview();
    void skillAtlas$preview(CompoundTag preview);
}
