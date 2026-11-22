package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.*;

public final class AtlasClientEvents {
    public static final EventGroup GROUP = EventGroup.of("SkillAtlasClientEvents");
    public static final EventHandler TOOLTIP = GROUP.client("tooltip", () -> TooltipEvent.class).supportsTarget(EventTargetType.STRING).hasResult().exceptionHandler((event, listener, error) -> { throw new IllegalStateException("Client tooltip callback failed", error); });
    private AtlasClientEvents() {}
    public static boolean post(TooltipEvent event) {
        EventResult result = AtlasEvents.post(TOOLTIP, event, event.nodeId);
        if (result.type() == EventResult.Type.ERROR) throw new IllegalStateException("Client tooltip callback failed");
        return !result.interruptFalse();
    }
}