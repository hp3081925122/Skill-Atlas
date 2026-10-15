package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public final class LootingBonuses {
    private record Roll(UUID player, int bonus) {}
    private static final ThreadLocal<Deque<Roll>> EQUIPMENT = ThreadLocal.withInitial(ArrayDeque::new);

    public static void begin(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            EQUIPMENT.get().push(new Roll(player.getUUID(), roll(player)));
        } else {
            EQUIPMENT.get().push(new Roll(null, 0));
        }
    }

    public static void end() {
        Deque<Roll> rolls = EQUIPMENT.get();
        if (!rolls.isEmpty()) rolls.pop();
        if (rolls.isEmpty()) EQUIPMENT.remove();
    }

    public static int equipment(ServerPlayer player) {
        Deque<Roll> rolls = EQUIPMENT.get();
        if (rolls.isEmpty()) {
            EQUIPMENT.remove();
            return roll(player);
        }
        Roll current = rolls.peek();
        return player.getUUID().equals(current.player()) ? current.bonus() : 0;
    }

    private static int roll(ServerPlayer player) {
        double value = SkillAttributes.value(player, SkillAttributes.LOOTING);
        return value > 0 ? SkillAttributes.roll(value, player.getRandom()) : 0;
    }

    private LootingBonuses() {}
}
