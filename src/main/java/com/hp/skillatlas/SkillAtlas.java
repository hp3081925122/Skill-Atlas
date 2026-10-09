package com.hp.skillatlas;

import com.mojang.logging.LogUtils;
import com.hp.skillatlas.network.AtlasNetwork;
import com.hp.skillatlas.server.AtlasEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SkillAtlas.ID)
public final class SkillAtlas {
    public static final String ID = "skill_atlas";
    public static final Logger LOGGER = LogUtils.getLogger();
    public SkillAtlas() { AtlasNetwork.register(); MinecraftForge.EVENT_BUS.register(new AtlasEvents()); }
}
