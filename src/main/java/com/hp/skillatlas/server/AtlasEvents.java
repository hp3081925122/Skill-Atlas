package com.hp.skillatlas.server;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class AtlasEvents {
    @SubscribeEvent public void started(ServerStartedEvent e) { AtlasService.start(e.getServer()); }
    @SubscribeEvent public void stopped(ServerStoppedEvent e) { AtlasService.stop(); }
    @SubscribeEvent public void commands(RegisterCommandsEvent e) { AtlasCommands.register(e.getDispatcher()); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) AtlasService.get().join(p); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) AtlasService.get().join(p); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) AtlasService.get().join(p); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p && AtlasService.get() != null) AtlasService.get().logout(p); }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) { if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && AtlasService.get() != null) AtlasService.get().tick(p); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void immunity(MobEffectEvent.Applicable e) {
        if (e.getEntity() instanceof ServerPlayer p && AtlasService.get() != null && AtlasService.get().rewards.immune(p, e.getEffectInstance().getEffect())) e.setResult(Event.Result.DENY);
    }
    @SubscribeEvent(priority = EventPriority.MONITOR) public void kill(LivingDeathEvent e) {
        if (!e.isCanceled() && e.getSource().getEntity() instanceof ServerPlayer p && eligible(p) && !(e.getEntity() instanceof net.minecraft.world.entity.player.Player) && AtlasService.get() != null)
            AtlasService.get().trigger(p, "kill", target -> target.startsWith("#") ? e.getEntity().getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(target.substring(1)))) : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType()).toString().equals(target));
    }
    @SubscribeEvent(priority = EventPriority.MONITOR) public void mine(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || !(e.getPlayer() instanceof ServerPlayer p) || !eligible(p) || AtlasService.get() == null) return;
        BlockState state = e.getState(); var pos = e.getPos().immutable(); var level = p.serverLevel();
        AtlasService.get().pendingBreak(p, level, pos, state);
    }
    private boolean eligible(ServerPlayer p) { return !(p instanceof FakePlayer) && !p.isCreative() && !p.isSpectator(); }
}
