package com.hp.skillatlas.server;

import com.hp.skillatlas.data.ExperienceMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

public final class ExperienceCosts {
    private ExperienceCosts() {
    }

    public static long available(Player player) {
        return ExperienceMath.available(player.experienceLevel, player.experienceProgress, player.getXpNeededForNextLevel());
    }

    public static boolean spend(ServerPlayer player, int amount) {
        long balance = available(player);
        if (amount < 0 || balance < amount) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        PlayerXpEvent.XpChange event = new PlayerXpEvent.XpChange(player, -amount);
        if (NeoForge.EVENT_BUS.post(event).isCanceled() || event.getAmount() != -amount) {
            return false;
        }
        balance = available(player);
        if (balance < amount) {
            return false;
        }
        set(player, balance - amount);
        return true;
    }

    public static void refund(ServerPlayer player, long amount) {
        long balance = available(player);
        set(player, balance > Long.MAX_VALUE - amount ? Long.MAX_VALUE : balance + amount);
    }

    private static void set(ServerPlayer player, long balance) {
        int level = ExperienceMath.levelFor(balance);
        player.setExperienceLevels(level);
        player.setExperiencePoints((int) (balance - ExperienceMath.atLevel(level)));
        player.totalExperience = (int) Math.min(Integer.MAX_VALUE, balance);
    }
}
