package com.hp.skillatlas.client;

import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.data.Icon;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public final class Icons {
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private Icons() {
    }

    public static ItemStack stack(Icon icon) {
        if (STACKS.size() > 4096) {
            STACKS.clear();
        }
        return STACKS.computeIfAbsent(icon.key(), k -> {
            ResourceLocation rl = ResourceLocation.tryParse(icon.id);
            Item item = null;
            if (rl != null) {
                item = ForgeRegistries.ITEMS.getValue(rl);
            }
            if (item == null || item == Items.AIR) {
                return new ItemStack(Items.BARRIER);
            }
            ItemStack stack = new ItemStack(item);
            if (!icon.nbt.isEmpty()) {
                try {
                    stack.setTag(TagParser.parseTag(icon.nbt));
                } catch (CommandSyntaxException ignored) {
                }
            }
            return stack;
        });
    }

    public static void draw(Icon icon, float x, float y, float size) {
        if (icon.isTexture()) {
            ResourceLocation rl = TEXTURES.computeIfAbsent(icon.id, k -> {
                ResourceLocation r = ResourceLocation.tryParse(k);
                if (r == null) {
                    return new ResourceLocation("minecraft", "textures/item/barrier.png");
                }
                return r;
            });
            Gfx.texture(rl, x, y, size, size, 0xFFFFFFFF);
            return;
        }
        Gfx.item(stack(icon), x, y, size);
    }
}
