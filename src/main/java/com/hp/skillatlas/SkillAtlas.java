package com.hp.skillatlas;

import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.server.FortuneModifier;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(SkillAtlas.MOD_ID)
public final class SkillAtlas {
    public static final String MOD_ID = "skill_atlas";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkillAtlas(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, AtlasConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, AtlasConfig.CLIENT_SPEC);
        SkillAttributes.register(bus);
        FortuneModifier.register(bus);
        AtlasNetwork.register(bus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
