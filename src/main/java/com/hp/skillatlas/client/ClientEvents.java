package com.hp.skillatlas.client;

import com.hp.skillatlas.SkillAtlas;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SkillAtlas.ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping OPEN = new KeyMapping("key.skill_atlas.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.skill_atlas");
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) while (OPEN.consumeClick()) if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) ClientState.request("open");
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientState.clear(); }
    @Mod.EventBusSubscriber(modid = SkillAtlas.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(OPEN); }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) manager -> { UiTheme.reload(manager); if (ClientState.screen != null) ClientState.screen.invalidateResources(); });
        }
    }
}
