package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class FurnaceSkills {
    private static final ThreadLocal<Double> EXPERIENCE = new ThreadLocal<>();
    public record InputSnapshot(AbstractFurnaceBlockEntity furnace, ItemStack input, ItemStack fuel) {
    }

    private FurnaceSkills() {
    }

    public static double value(AbstractFurnaceBlockEntity furnace, RegistryObject<Attribute> attribute) {
        if (!(furnace.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var owner = ((FurnaceState) furnace).skillAtlas$owner();
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        return player == null ? 0 : SkillAttributes.value(player, attribute);
    }

    public static InputSnapshot beforeClick(AbstractContainerMenu menu, Player player) {
        if (player instanceof ServerPlayer && menu instanceof AbstractFurnaceMenu
                && menu.getSlot(0).container instanceof AbstractFurnaceBlockEntity furnace) {
            return new InputSnapshot(furnace, furnace.getItem(0).copy(), furnace.getItem(1).copy());
        }
        return null;
    }

    public static void afterClick(InputSnapshot snapshot, Player player) {
        if (snapshot != null && (added(snapshot.input, snapshot.furnace.getItem(0))
                || added(snapshot.fuel, snapshot.furnace.getItem(1)))) {
            FurnaceState state = (FurnaceState) snapshot.furnace;
            if (!player.getUUID().equals(state.skillAtlas$owner())) {
                state.skillAtlas$owner(player.getUUID());
                snapshot.furnace.setChanged();
                SkillAtlas.LOGGER.debug("Furnace operator bound: player={}, furnace={}", player.getScoreboardName(), snapshot.furnace.getBlockPos());
            }
        }
    }

    private static boolean added(ItemStack before, ItemStack after) {
        return !after.isEmpty() && (!ItemStack.isSameItemSameTags(before, after) || after.getCount() > before.getCount());
    }

    public static int advance(AbstractFurnaceBlockEntity furnace, int requested) {
        FurnaceState state = (FurnaceState) furnace;
        int previous = state.skillAtlas$progress();
        if (requested == previous + 1 && previous < state.skillAtlas$totalTime()) {
            double speed = value(furnace, SkillAttributes.SMELTING_SPEED);
            if (speed <= 0) {
                state.skillAtlas$carry(0);
                return requested;
            }
            double amount = 1 + speed / 100.0D + state.skillAtlas$carry();
            int ticks = (int) Math.floor(amount + 1e-9);
            state.skillAtlas$carry(Math.max(0, amount - ticks));
            return (int) Math.min(state.skillAtlas$totalTime(), (long) previous + ticks);
        }
        if (requested <= previous) {
            state.skillAtlas$carry(0);
        }
        return requested;
    }

    public static List<Recipe<?>> collectExperience(AbstractFurnaceBlockEntity furnace, ServerLevel level, Vec3 pos) {
        Double previous = EXPERIENCE.get();
        EXPERIENCE.set(value(furnace, SkillAttributes.SMELTING_EXPERIENCE));
        try {
            return furnace.getRecipesToAwardAndPopExperience(level, pos);
        } finally {
            if (previous == null) {
                EXPERIENCE.remove();
            } else {
                EXPERIENCE.set(previous);
            }
        }
    }

    public static int experienceAmount(int original, ServerLevel level) {
        Double bonus = EXPERIENCE.get();
        if (bonus == null || bonus <= 0) {
            return original;
        }
        return SkillAttributes.roll(Math.min(Integer.MAX_VALUE, original * (1 + bonus / 100.0D)), level.getRandom());
    }
}
