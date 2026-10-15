package com.hp.skillatlas;

import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.ItemPlan;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemCosts {
    private static final Map<String, Optional<CompoundTag>> TAGS = new ConcurrentHashMap<>();

    private ItemCosts() {
    }

    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return Items.AIR;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) {
            return Items.AIR;
        }
        return item;
    }

    public static CompoundTag tag(String nbt) {
        if (TAGS.size() > 1024) {
            TAGS.clear();
        }
        return TAGS.computeIfAbsent(nbt, s -> {
            try {
                return Optional.of(TagParser.parseTag(s));
            } catch (CommandSyntaxException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static List<ItemStack> slots(Inventory inventory) {
        List<ItemStack> out = new ArrayList<>(inventory.items.size() + inventory.offhand.size());
        out.addAll(inventory.items);
        out.addAll(inventory.offhand);
        return out;
    }

    public static String serialize(ItemStack stack, HolderLookup.Provider registries) {
        return stack.copyWithCount(1).save(registries).toString();
    }

    public static ItemStack stack(String id, String nbt, HolderLookup.Provider registries) {
        Item item = item(id);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        if (nbt.isEmpty()) {
            return new ItemStack(item);
        }
        CompoundTag data = tag(nbt);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        ItemStack parsed = ItemStack.parseOptional(registries, data);
        return parsed.is(item) ? parsed : ItemStack.EMPTY;
    }

    private static int[] plan(List<ItemStack> slots, List<ItemCost> costs, int[] got, HolderLookup.Provider registries) {
        Item[] items = new Item[costs.size()];
        ItemStack[] exact = new ItemStack[costs.size()];
        for (int i = 0; i < costs.size(); i++) {
            ItemCost c = costs.get(i);
            items[i] = item(c.item);
            if (!c.nbt.isEmpty()) {
                exact[i] = stack(c.item, c.nbt, registries);
                if (exact[i].isEmpty()) {
                    items[i] = Items.AIR;
                }
            }
        }
        int[] counts = new int[slots.size()];
        boolean[] plain = new boolean[slots.size()];
        for (int s = 0; s < slots.size(); s++) {
            ItemStack stack = slots.get(s);
            if (!stack.isEmpty()) {
                counts[s] = stack.getCount();
                plain[s] = stack.getComponentsPatch().isEmpty();
            }
        }
        return ItemPlan.plan(costs, counts, plain, (i, s) -> {
            ItemStack stack = slots.get(s);
            if (items[i] == Items.AIR || !stack.is(items[i])) {
                return false;
            }
            if (exact[i] == null) {
                return true;
            }
            return ItemStack.isSameItemSameComponents(exact[i], stack);
        }, got);
    }

    public static boolean has(Inventory inventory, List<ItemCost> costs) {
        if (costs.isEmpty()) {
            return true;
        }
        return plan(slots(inventory), costs, new int[costs.size()], inventory.player.registryAccess()) != null;
    }

    public static int[] owned(Inventory inventory, List<ItemCost> costs) {
        int[] got = new int[costs.size()];
        if (!costs.isEmpty()) {
            plan(slots(inventory), costs, got, inventory.player.registryAccess());
        }
        return got;
    }

    public static boolean consume(Inventory inventory, List<ItemCost> costs) {
        if (costs.isEmpty()) {
            return true;
        }
        List<ItemStack> slots = slots(inventory);
        int[] used = plan(slots, costs, new int[costs.size()], inventory.player.registryAccess());
        if (used == null) {
            return false;
        }
        for (int s = 0; s < used.length; s++) {
            if (used[s] > 0) {
                slots.get(s).shrink(used[s]);
            }
        }
        inventory.setChanged();
        return true;
    }
}
