package com.hp.skillatlas.kubejs;

import com.hp.skillatlas.KubeJSSupport;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;

public final class AtlasPlugin implements KubeJSPlugin {
    @Override public void registerEvents(EventGroupRegistry registry) {
        if (!KubeJSSupport.active()) return;
        registry.register(AtlasEvents.GROUP); registry.register(AtlasClientEvents.GROUP);
    }
    @Override public void registerBindings(BindingRegistry event) {
        if (!KubeJSSupport.active()) return;
        if (event.type() == ScriptType.SERVER) event.add("SkillAtlas", AtlasAPI.class);
        if (event.type() == ScriptType.CLIENT && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) event.add("SkillAtlasClient", com.hp.skillatlas.kubejs.client.ClientAPI.class);
    }
    @Override public void registerClasses(ClassFilter filter) {
        if (!KubeJSSupport.active()) return;
        if (filter.scriptType == ScriptType.SERVER) {
            for (Class<?> api : new Class<?>[]{AtlasAPI.class, PlayerAPI.class, Outcome.class, AtlasEvent.class, LearningCostEvent.class, DefinitionsEvent.class, NodeBuilder.class, RankBuilder.class, CategoryBuilder.class, PoolBuilder.class, ConditionTypesEvent.class, RewardTypesEvent.class, ConditionType.class, RewardType.class, ConditionContext.class, RewardContext.class, AcquisitionTypesEvent.class, AcquisitionType.class, AcquisitionContext.class}) filter.allow(api);
        }
        if (filter.scriptType == ScriptType.CLIENT && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) { filter.allow(com.hp.skillatlas.kubejs.client.ClientAPI.class); filter.allow(TooltipEvent.class); filter.allow(Outcome.class); }
    }
    @Override public void afterScriptsLoaded(ScriptManager manager) {
        if (KubeJSSupport.active() && manager.scriptType == ScriptType.SERVER) Hooks.requestReload();
    }
}