package com.hp.skillatlas.data;

public enum RewardOperation {
    ADD("add"),
    MULTIPLY_BASE("multiply_base"),
    MULTIPLY_TOTAL("multiply_total");

    public final String key;

    RewardOperation(String key) {
        this.key = key;
    }

    public static RewardOperation byKey(String key) {
        for (RewardOperation o : values()) {
            if (o.key.equals(key)) {
                return o;
            }
        }
        throw new AtlasException("skill_atlas.error.bad_operation", key);
    }
}
