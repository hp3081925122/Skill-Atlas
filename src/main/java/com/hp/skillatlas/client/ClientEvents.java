package com.hp.skillatlas.client;

import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.client.screen.AtlasScreen;
import com.hp.skillatlas.data.Texts;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final KeyMapping OPEN = new KeyMapping("key.skill_atlas.open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.skill_atlas");

    private ClientEvents() {
    }

    @EventBusSubscriber(modid = SkillAtlas.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            Texts.lookup = raw -> ClientAtlas.definition.texts.display(raw, Minecraft.getInstance().getLanguageManager().getSelected());
        }
    }

    @EventBusSubscriber(modid = SkillAtlas.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
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
        }
    }
}
