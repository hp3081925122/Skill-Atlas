package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.data.RuleType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PermissionsChangedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SkillAtlas.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        AtlasServer.start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AtlasServer.stop();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        AtlasCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && AtlasServer.server() != null) {
            AtlasServer.tick();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && AtlasServer.server() != null) {
            RewardManager.tick(player);
            if (player.tickCount % 20 == 0) {
                PointTracker.second(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AtlasServer.login(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AtlasServer.logout(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RewardManager.refresh(player);
            if (!event.isEndConquered()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    @SubscribeEvent
    public static void onPermissions(PermissionsChangedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AtlasServer.markSync(player);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer player && RewardManager.immune(player, event.getEffectInstance().getEffect())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            double bonus = SkillAttributes.value(player, SkillAttributes.HEALING_EFFICIENCY);
            if (bonus != 0.0D) {
                event.setAmount((float) (event.getAmount() * (1.0D + bonus / 100.0D)));
            }
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer player && event.getDroppedExperience() > 0) {
            double bonus = SkillAttributes.value(player, SkillAttributes.EXPERIENCE_GAIN);
            if (bonus != 0.0D) {
                event.setDroppedExperience(SkillAttributes.roll(event.getDroppedExperience() * (1.0D + bonus / 100.0D), player.getRandom()));
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        double bonus = SkillAttributes.value(event.getEntity(), SkillAttributes.MINING_SPEED);
        if (bonus != 0.0D) {
            event.setNewSpeed(event.getNewSpeed() * (float) Math.max(0.0D, 1.0D + bonus / 100.0D));
        }
    }

    @SubscribeEvent
    public static void onLooting(LootingLevelEvent event) {
        DamageSource source = event.getDamageSource();
        if (source != null && source.getEntity() instanceof ServerPlayer player) {
            double bonus = SkillAttributes.value(player, SkillAttributes.LOOTING);
            if (bonus > 0.0D) {
                event.setLootingLevel(event.getLootingLevel() + SkillAttributes.roll(bonus, player.getRandom()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity()) {
            PointTracker.kill(player, event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
            PointTracker.mine(player, event.getState());
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PointTracker.item(player, RuleType.CRAFT, event.getCrafting());
        }
    }

    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PointTracker.item(player, RuleType.SMELT, event.getSmelting());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFish(ItemFishedEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            for (ItemStack stack : event.getDrops()) {
                PointTracker.item(player, RuleType.FISH, stack);
            }
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PointTracker.advancement(player, event.getAdvancement().getId(), event.getAdvancement().getDisplay() != null);
        }
    }
}
