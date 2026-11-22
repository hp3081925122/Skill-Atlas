package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.*;
import org.slf4j.LoggerFactory;

public final class AtlasEvents {
    public static final EventGroup GROUP = EventGroup.of("SkillAtlasEvents");
    public static final EventHandler DEFINITIONS = guarded(GROUP.server("definitions", () -> DefinitionsEvent.class).hasResult());
    public static final EventHandler CONDITION_TYPES = guarded(GROUP.server("conditionTypes", () -> ConditionTypesEvent.class).hasResult());
    public static final EventHandler REWARD_TYPES = guarded(GROUP.server("rewardTypes", () -> RewardTypesEvent.class).hasResult());
    public static final EventHandler ACQUISITION_TYPES = guarded(GROUP.server("acquisitionTypes", () -> AcquisitionTypesEvent.class).hasResult());
    public static final EventHandler LEARNING_COST = guarded(GROUP.server("learningCost", () -> LearningCostEvent.class).supportsTarget(EventTargetType.STRING).hasResult());
    public static final EventHandler BEFORE_LEARN = target("beforeLearn", true);
    public static final EventHandler LEARNED = target("learned", false);
    public static final EventHandler LEARN_FAILED = target("learnFailed", false);
    public static final EventHandler BEFORE_RESET = target("beforeReset", true);
    public static final EventHandler RESET = target("reset", false);
    public static final EventHandler POINTS_CHANGING = target("pointsChanging", true);
    public static final EventHandler POINTS_CHANGED = target("pointsChanged", false);
    public static final EventHandler PROGRESSING = target("progressing", true);
    public static final EventHandler LEVEL_CHANGED = target("levelChanged", false);
    public static final EventHandler RELOADED = GROUP.server("reloaded", () -> AtlasEvent.class);
    public static final EventHandler RELOAD_FAILED = GROUP.server("reloadFailed", () -> AtlasEvent.class);
    private AtlasEvents() {}

    private static EventHandler guarded(EventHandler handler) {
        return handler.exceptionHandler((event, listener, error) -> {
            throw new IllegalStateException("Skill Atlas script callback failed for " + handler + " at " + listener, error);
        });
    }

    private static EventHandler target(String name, boolean cancel) {
        EventHandler handler = GROUP.server(name, () -> AtlasEvent.class).supportsTarget(EventTargetType.STRING);
        return cancel ? guarded(handler.hasResult()) : handler;
    }

    public static boolean denied(EventHandler handler, AtlasEvent event, String target) {
        if (!handler.hasListeners()) return false;
        EventResult result = post(handler, event, target);
        if (result.type() == EventResult.Type.ERROR) {
            event.callbackFailed = true;
            event.denial = net.minecraft.network.chat.Component.translatable("skill_atlas_kubejs.script_error");
            return true;
        }
        return result.interruptFalse();
    }

    public static void after(EventHandler handler, AtlasEvent event, String target) {
        if (handler.hasListeners()) post(handler, event, target);
    }

    @SuppressWarnings("unchecked")
    public static EventResult post(EventHandler handler, KubeEvent event, String target) {
        try {
            return handler instanceof TargetedEventHandler<?> targeted ? ((TargetedEventHandler<String>)targeted).post(event, target) : handler.post(event);
        } catch (RuntimeException error) {
            LoggerFactory.getLogger("skill_atlas_kubejs").error("Skill Atlas event dispatch failed for {}", handler, error);
            return EventResult.Type.ERROR.exit(null, error).result;
        }
    }
}
