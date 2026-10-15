package com.hp.skillatlas.server;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.SkillAttributes;
import com.hp.skillatlas.data.RuleType;
import com.hp.skillatlas.data.MovementMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SkillAtlas.MOD_ID)
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
    public static void onServerTick(ServerTickEvent.Post event) {
        if (AtlasServer.server() != null) {
            AtlasServer.tick();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && AtlasServer.server() != null) {
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
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && !player.isInWater() && !player.isInLava()
                && !player.getAbilities().flying) {
            double height = SkillAttributes.value(player, SkillAttributes.JUMP_HEIGHT);
            if (height > 0) {
                var movement = player.getDeltaMovement();
                double gravity = player.getAttributeValue(Attributes.GRAVITY);
                player.setDeltaMovement(movement.x, MovementMath.jumpVelocity(movement.y, height, gravity), movement.z);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFallDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().is(DamageTypeTags.IS_FALL)) {
            double reduction = SkillAttributes.value(player, SkillAttributes.FALL_DAMAGE_REDUCTION);
            if (reduction > 0) {
                float original = event.getNewDamage();
                event.setNewDamage((float) (original * Math.max(0, 1 - reduction / 100.0D)));
                SkillAtlas.LOGGER.debug("Fall damage reduced: player={}, reduction={}, damage={} -> {}",
                        player.getScoreboardName(), reduction, original, event.getNewDamage());
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!event.isCanceled() && event.getBreaker() instanceof ServerPlayer player
                && !player.getAbilities().instabuild && !player.isSpectator()
                && event.getState().is(Tags.Blocks.ORES) && event.getDroppedExperience() > 0) {
            double bonus = SkillAttributes.value(player, SkillAttributes.MINING_EXPERIENCE);
            if (bonus != 0.0D) {
                int original = event.getDroppedExperience();
                double scaled = Math.min(Integer.MAX_VALUE, original * Math.max(0.0D, 1.0D + bonus / 100.0D));
                event.setDroppedExperience(SkillAttributes.roll(scaled, player.getRandom()));
                SkillAtlas.LOGGER.debug("Mining experience bonus for {} at {}: {} -> {}", player.getGameProfile().getName(), event.getPos(), original, event.getDroppedExperience());
            }
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PointTracker.item(player, RuleType.CRAFT, event.getCrafting());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFish(ItemFishedEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            PointTracker.fish(player, event.getDrops());
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PointTracker.advancement(player, event.getAdvancement().id(), event.getAdvancement().value().display().isPresent());
        }
    }
}
