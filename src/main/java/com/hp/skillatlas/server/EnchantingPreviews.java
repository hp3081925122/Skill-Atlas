package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.network.EnchantingPreviewPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

public final class EnchantingPreviews {
    private EnchantingPreviews() {
    }

    public static void sync(Player player, EnchantmentMenu menu) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        EnchantmentMenuState state = (EnchantmentMenuState) menu;
        CompoundTag preview = new CompoundTag();
        if (SkillAttributes.value(player, SkillAttributes.ENCHANTING_INSIGHT) > 0) {
            ItemStack input = menu.getSlot(0).getItem();
            preview.putInt("Seed", menu.getEnchantmentSeed());
            preview.put("Input", input.save(new CompoundTag()));
            preview.putIntArray("Costs", menu.costs);
            ListTag offers = new ListTag();
            for (int slot = 0; slot < 3; slot++) {
                ListTag entries = new ListTag();
                if (!input.isEmpty() && menu.costs[slot] > 0) {
                    for (var entry : state.skillAtlas$options(input, slot, menu.costs[slot])) {
                        var id = ForgeRegistries.ENCHANTMENTS.getKey(entry.enchantment);
                        if (id != null) {
                            CompoundTag tag = new CompoundTag();
                            tag.putString("Id", id.toString());
                            tag.putInt("Level", entry.level);
                            entries.add(tag);
                        }
                    }
                }
                CompoundTag offer = new CompoundTag();
                offer.put("Entries", entries);
                offers.add(offer);
            }
            preview.put("Offers", offers);
        } else if (state.skillAtlas$preview().isEmpty()) {
            return;
        }
        state.skillAtlas$preview(preview);
        AtlasNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new EnchantingPreviewPacket(menu.containerId, preview));
    }
}
