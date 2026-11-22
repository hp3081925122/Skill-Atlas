package com.hp.skillatlas.client;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.client.screen.AtlasScreen;
import com.hp.skillatlas.data.Texts;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final KeyMapping OPEN = new KeyMapping("key.skill_atlas.open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.skill_atlas");
    public static final KeyMapping REFUND = new KeyMapping("key.skill_atlas.refund", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.skill_atlas");

    private ClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = SkillAtlas.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
            event.register(REFUND);
        }

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            Texts.lookup = raw -> ClientAtlas.definition.texts.display(raw, Minecraft.getInstance().getLanguageManager().getSelected());
        }

        @SubscribeEvent
        public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) rm -> GifTextures.clear());
        }
    }

    @Mod.EventBusSubscriber(modid = SkillAtlas.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player == null || mc.screen != null) {
                    continue;
                }
                if (!ClientAtlas.ready) {
                    mc.player.displayClientMessage(Component.translatable("skill_atlas.message.not_ready"), true);
                    ClientAtlas.requestResync();
                    continue;
                }
                mc.setScreen(new AtlasScreen());
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientPackets.reset();
            GifTextures.clear();
        }
    }
}
