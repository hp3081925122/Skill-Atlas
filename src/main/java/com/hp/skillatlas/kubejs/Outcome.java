package com.hp.skillatlas.kubejs;

import net.minecraft.network.chat.Component;

public final class Outcome {
    public final boolean ok;
    public final String code;
    public final Component message;
    public long actualDelta;
    public int oldLevel;
    public int newLevel;
    public String transactionId = "";
    public Object detail;

    private Outcome(boolean ok, String code, Component message) {
        this.ok = ok;
        this.code = code;
        this.message = message;
    }

    public static Outcome ok() {
        return new Outcome(true, "ok", Component.translatable("skill_atlas_kubejs.ok"));
    }

    public static Outcome fail(String code) {
        return new Outcome(false, code, Component.translatable("skill_atlas_kubejs." + code));
    }
    public static Outcome fail(String code, Component message) { return new Outcome(false, code, message); }

    public static Outcome denied(Component message) {
        return new Outcome(false, "script_denied", message);
    }
}
