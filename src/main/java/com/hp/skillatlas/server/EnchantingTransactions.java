package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.data.ExperienceMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.function.BiConsumer;

public final class EnchantingTransactions {
    private EnchantingTransactions() {
    }

    public static void run(Player player, EnchantmentMenu menu, ContainerLevelAccess access, BiConsumer<Level, BlockPos> action) {
        access.execute((level, pos) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || player.getAbilities().instabuild) {
                action.accept(level, pos);
                return;
            }
            double saving = SkillAttributes.value(player, SkillAttributes.LAPIS_SAVING);
            double refund = SkillAttributes.value(player, SkillAttributes.ENCHANTING_REFUND);
            double copy = SkillAttributes.value(player, SkillAttributes.BOOK_COPY);
            if (saving <= 0 && refund <= 0 && copy <= 0) {
                action.accept(level, pos);
                return;
            }
            ItemStack before = menu.getSlot(0).getItem().copy();
            ItemStack lapis = menu.getSlot(1).getItem().copy();
            long experience = ExperienceCosts.available(player);
            int seed = player.getEnchantmentSeed();
            action.accept(level, pos);
            ItemStack result = menu.getSlot(0).getItem();
            if (seed == player.getEnchantmentSeed() && ItemStack.isSameItemSameTags(before, result)) {
                return;
            }
            ItemStack remaining = menu.getSlot(1).getItem();
            if (lapis.is(Items.LAPIS_LAZULI) && (remaining.isEmpty() || ItemStack.isSameItemSameTags(lapis, remaining))
                    && lapis.getCount() > remaining.getCount() && saving > 0 && player.getRandom().nextDouble() < saving / 100.0D) {
                menu.getSlot(1).set(lapis);
            }
            long spent = Math.max(0, experience - ExperienceCosts.available(player));
            long returned = ExperienceMath.percentageRefund(spent, refund);
            if (returned > 0) {
                ExperienceCosts.refund(serverPlayer, returned);
            }
            boolean duplicated = before.is(Items.BOOK) && result.is(Items.ENCHANTED_BOOK)
                    && copy > 0 && player.getRandom().nextDouble() < copy / 100.0D;
            if (duplicated) {
                ItemStack extra = result.copy();
                extra.setCount(1);
                if (!player.getInventory().add(extra) && !extra.isEmpty()) {
                    player.drop(extra, false);
                }
            }
            menu.broadcastChanges();
            SkillAtlas.LOGGER.debug("Enchanting major bonuses: player={}, experienceSpent={}, experienceRefunded={}, bookCopied={}",
                    player.getScoreboardName(), spent, returned, duplicated);
        });
    }
}
