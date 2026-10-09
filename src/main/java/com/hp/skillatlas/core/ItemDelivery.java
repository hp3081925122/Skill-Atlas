package com.hp.skillatlas.core;

import com.hp.skillatlas.model.Definition;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class ItemDelivery {
    private ItemDelivery() {}
    public static String validate(Definition.ItemCost cost) {
        if (cost == null || cost.count < 1 || cost.count > 1_000_000 || cost.nbt == null || cost.nbt.length() > 8192) return "Invalid item quantity or NBT";
        ResourceLocation key = cost.item == null ? null : ResourceLocation.tryParse(cost.item);
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key) || ForgeRegistries.ITEMS.getValue(key) == Items.AIR) return "Unknown delivery item: " + cost.item;
        if (!cost.nbt.isBlank()) try { TagParser.parseTag(cost.nbt); } catch (Exception ex) { return "Invalid delivery NBT: " + cost.item; }
        return null;
    }
    private static List<ItemStack> slots(Inventory inventory) {
        List<ItemStack> slots = new ArrayList<>(inventory.items); slots.addAll(inventory.offhand); return slots;
    }
    public static int[] plan(List<ItemStack> slots, List<Definition.ItemCost> costs) {
        Map<Definition.ItemCost, Item> items = new IdentityHashMap<>();
        Map<Definition.ItemCost, CompoundTag> tags = new IdentityHashMap<>();
        for (var cost : costs) {
            if (validate(cost) != null) return null;
            items.put(cost, ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(cost.item)));
            if (!cost.nbt.isBlank()) try { tags.put(cost, TagParser.parseTag(cost.nbt)); } catch (Exception ex) { return null; }
        }
        int[] counts = slots.stream().mapToInt(ItemStack::getCount).toArray();
        return ItemAllocation.plan(costs, counts, (cost, index) -> {
            ItemStack stack = slots.get(index);
            return !stack.isEmpty() && stack.is(items.get(cost)) && (!tags.containsKey(cost) || tags.get(cost).equals(stack.getTag()));
        });
    }
    public static boolean available(Inventory inventory, List<Definition.ItemCost> costs) {
        return costs.isEmpty() || plan(slots(inventory), costs) != null;
    }
    public static boolean consume(Inventory inventory, List<Definition.ItemCost> costs) {
        if (costs.isEmpty()) return true;
        List<ItemStack> slots = slots(inventory); int[] consumed = plan(slots, costs);
        if (consumed == null) return false;
        for (int slot = 0; slot < consumed.length; slot++) if (consumed[slot] > 0) slots.get(slot).shrink(consumed[slot]);
        inventory.setChanged(); return true;
    }
}
