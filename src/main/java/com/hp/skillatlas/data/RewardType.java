package com.hp.skillatlas.data;

public enum RewardType {
    ATTRIBUTE("attribute"),
    EFFECT("effect"),
    IMMUNITY("immunity"),
    COMMAND("command");

    public final String key;

    RewardType(String key) {
        this.key = key;
    }

    public static RewardType byKey(String key) {
        for (RewardType t : values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        throw new AtlasException("skill_atlas.error.bad_reward", key);
    }
}
