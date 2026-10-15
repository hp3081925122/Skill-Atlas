package com.hp.skillatlas;

import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.server.FortuneModifier;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
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
        bus.addListener(AtlasNetwork::register);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
