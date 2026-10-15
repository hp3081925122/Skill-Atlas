package com.hp.skillatlas;

import com.hp.skillatlas.data.ItemCost;
import com.hp.skillatlas.data.ItemPlan;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentPatch;

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
        Identifier rl = Identifier.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return Items.AIR;
        }
        Item item = BuiltInRegistries.ITEM.getValue(rl);
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
                return Optional.of(TagParser.parseCompoundFully(s));
            } catch (CommandSyntaxException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static List<ItemStack> slots(Inventory inventory) {
        List<ItemStack> out = new ArrayList<>(inventory.getNonEquipmentItems());
        out.add(inventory.player.getOffhandItem());
        return out;
    }

    private static int[] plan(List<ItemStack> slots, List<ItemCost> costs, int[] got) {
        Item[] items = new Item[costs.size()];
        DataComponentPatch[] tags = new DataComponentPatch[costs.size()];
        for (int i = 0; i < costs.size(); i++) {
            ItemCost c = costs.get(i);
            items[i] = item(c.item);
            if (!c.nbt.isEmpty()) {
                try {
                    tags[i] = StackComponents.patch(c.nbt);
                } catch (RuntimeException e) {
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
            if (tags[i] == null) {
                return true;
            }
            return tags[i].equals(stack.getComponentsPatch());
        }, got);
    }

    public static boolean has(Inventory inventory, List<ItemCost> costs) {
        if (costs.isEmpty()) {
            return true;
        }
        return plan(slots(inventory), costs, new int[costs.size()]) != null;
    }

    public static int[] owned(Inventory inventory, List<ItemCost> costs) {
        int[] got = new int[costs.size()];
        if (!costs.isEmpty()) {
            plan(slots(inventory), costs, got);
        }
        return got;
    }

    public static boolean consume(Inventory inventory, List<ItemCost> costs) {
        if (costs.isEmpty()) {
            return true;
        }
        List<ItemStack> slots = slots(inventory);
        int[] used = plan(slots, costs, new int[costs.size()]);
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
