package com.hp.skillatlas;

import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.server.FortuneModifier;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SkillAtlas.MOD_ID)
public final class SkillAtlas {
    public static final String MOD_ID = "skill_atlas";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkillAtlas() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AtlasConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AtlasConfig.CLIENT_SPEC);
        SkillAttributes.register(FMLJavaModLoadingContext.get().getModEventBus());
        FortuneModifier.register(FMLJavaModLoadingContext.get().getModEventBus());
        AtlasNetwork.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
