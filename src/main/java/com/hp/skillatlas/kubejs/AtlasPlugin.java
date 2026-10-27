package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.KubeJSSupport;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public final class AtlasPlugin extends KubeJSPlugin {
    @Override public void registerEvents() {
        if (!KubeJSSupport.active()) return;
        AtlasEvents.GROUP.register(); AtlasClientEvents.GROUP.register();
    }
    @Override public void registerBindings(BindingsEvent event) {
        if (!KubeJSSupport.active()) return;
        if (event.getType() == ScriptType.SERVER) event.add("SkillAtlas", AtlasAPI.class);
        if (event.getType() == ScriptType.CLIENT && net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) event.add("SkillAtlasClient", com.hp.skillatlas.kubejs.client.ClientAPI.class);
    }
    @Override public void registerClasses(ScriptType type, ClassFilter filter) {
        if (!KubeJSSupport.active()) return;
        if (type == ScriptType.SERVER) {
            for (Class<?> api : new Class<?>[]{AtlasAPI.class, PlayerAPI.class, Outcome.class, AtlasEvent.class, LearningCostEvent.class, DefinitionsEvent.class, NodeBuilder.class, RankBuilder.class, CategoryBuilder.class, PoolBuilder.class, ConditionTypesEvent.class, RewardTypesEvent.class, ConditionType.class, RewardType.class, ConditionContext.class, RewardContext.class, AcquisitionTypesEvent.class, AcquisitionType.class, AcquisitionContext.class}) filter.allow(api);
        }
        if (type == ScriptType.CLIENT && net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) { filter.allow(com.hp.skillatlas.kubejs.client.ClientAPI.class); filter.allow(TooltipEvent.class); filter.allow(Outcome.class); }
    }
    @Override public void onServerReload() { if (KubeJSSupport.active()) Hooks.requestReload(); }
}
