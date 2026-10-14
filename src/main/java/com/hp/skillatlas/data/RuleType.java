package com.hp.skillatlas.data;

public enum RuleType {
    KILL("kill", Targets.ENTITY),
    MINE("mine", Targets.BLOCK),
    CRAFT("craft", Targets.ITEM),
    SMELT("smelt", Targets.ITEM),
    FISH("fish", Targets.ITEM),
    ADVANCEMENT("advancement", Targets.ADVANCEMENT),
    LEVEL("level", Targets.NONE),
    PLAYTIME("playtime", Targets.NONE);

    public enum Targets {
        ENTITY, BLOCK, ITEM, ADVANCEMENT, NONE
    }

    public final String key;
    public final Targets targets;

    RuleType(String key, Targets targets) {
        this.key = key;
        this.targets = targets;
    }

    public boolean acceptsTags() {
        return targets == Targets.ENTITY || targets == Targets.BLOCK || targets == Targets.ITEM;
    }

    public static RuleType byKey(String key) {
        for (RuleType t : values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        throw new AtlasException("skill_atlas.error.bad_rule_type", key);
    }
}
