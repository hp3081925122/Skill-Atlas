package com.hp.skillatlas.client;

import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.server.EnchantmentMenuState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EnchantingTooltip {
    private EnchantingTooltip() {
    }

    public static List<Component> expand(EnchantmentMenu menu, List<Component> original, int mouseX, int mouseY, int left, int top) {
        var player = Minecraft.getInstance().player;
        if (player == null || SkillAttributes.value(player, SkillAttributes.ENCHANTING_INSIGHT) <= 0) {
            return original;
        }
        int x = mouseX - left;
        int y = mouseY - top;
        int slot = -1;
        for (int i = 0; i < 3; i++) {
            if (x >= 59 && x < 169 && y >= 13 + 19 * i && y < 32 + 19 * i) {
                slot = i;
                break;
            }
        }
        CompoundTag preview = ((EnchantmentMenuState) menu).skillAtlas$preview();
        if (slot < 0 || preview.isEmpty() || preview.getInt("Seed") != menu.getEnchantmentSeed()
                || !Arrays.equals(preview.getIntArray("Costs"), menu.costs)
                || !ItemStack.isSameItemSameComponents(ItemStack.parseOptional(player.registryAccess(), preview.getCompound("Input")), menu.getSlot(0).getItem())) {
            return original;
        }
        var offers = preview.getList("Offers", Tag.TAG_COMPOUND);
        if (offers.size() != 3) {
            return original;
        }
        var entries = offers.getCompound(slot).getList("Entries", Tag.TAG_COMPOUND);
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            var enchantment = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(ResourceLocation.parse(entry.getString("Id"))).orElse(null);
            if (enchantment != null) {
                lines.add(Enchantment.getFullname(enchantment, entry.getInt("Level")));
            }
        }
        if (lines.isEmpty()) {
            return original;
        }
        List<Component> result = new ArrayList<>();
        result.add(Component.translatable("skill_atlas.ui.enchanting.preview").withStyle(ChatFormatting.GOLD));
        result.addAll(lines);
        if (original.size() > 1) {
            result.addAll(original.subList(1, original.size()));
        }
        return result;
    }
}
