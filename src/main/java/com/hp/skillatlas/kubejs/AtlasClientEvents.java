package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.*;
import dev.latvian.mods.kubejs.script.ScriptType;

public final class AtlasClientEvents {
    public static final EventGroup GROUP = EventGroup.of("SkillAtlasClientEvents");
    public static final EventHandler TOOLTIP = GROUP.client("tooltip", () -> TooltipEvent.class).extra(Extra.STRING).hasResult();
    private AtlasClientEvents() {}
    public static boolean post(TooltipEvent event) {
        Throwable[] failure = new Throwable[1];
        EventResult result = TOOLTIP.post(ScriptType.CLIENT, event.nodeId, event, (value, listener, error) -> { failure[0] = error; return error; });
        if (failure[0] != null || result.error()) throw new IllegalStateException("Client tooltip callback failed", failure[0]);
        return !result.interruptFalse();
    }
}
