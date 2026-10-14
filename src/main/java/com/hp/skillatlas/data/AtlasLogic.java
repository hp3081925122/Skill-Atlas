package com.hp.skillatlas.data;

public final class AtlasLogic {
    public enum State {
        LOCKED, UNAFFORDABLE, AVAILABLE, LEARNED, UPGRADABLE, MAXED
    }

    public enum Check {
        OK, MISSING, MAXED, LOCKED, NO_POINTS
    }

    private AtlasLogic() {
    }

    public static boolean prerequisitesMet(AtlasDefinition d, SkillNode n, PlayerProgress p) {
        int total = 0;
        int met = 0;
        for (Prerequisite pre : n.prerequisites) {
            if (!d.nodes.containsKey(pre.node)) {
                continue;
            }
            total++;
            if (p.level(pre.node) > 0) {
                met++;
            }
        }
        if (total == 0) {
            return true;
        }
        if (n.requireAll) {
            return met == total;
        }
        return met > 0;
    }

    public static boolean visible(AtlasDefinition d, SkillNode n, PlayerProgress p) {
        if (!n.hidden || p.level(n.id) > 0) {
            return true;
        }
        return prerequisitesMet(d, n, p);
    }

    public static Check check(AtlasDefinition d, SkillNode n, PlayerProgress p) {
        if (n == null) {
            return Check.MISSING;
        }
        int level = p.level(n.id);
        if (level >= n.maxLevel()) {
            return Check.MAXED;
        }
        if (!prerequisitesMet(d, n, p)) {
            return Check.LOCKED;
        }
        if (p.points(d.poolOf(n)) < n.levels.get(level).cost) {
            return Check.NO_POINTS;
        }
        return Check.OK;
    }

    public static State state(AtlasDefinition d, SkillNode n, PlayerProgress p) {
        int level = p.level(n.id);
        Check c = check(d, n, p);
        if (c == Check.MAXED) {
            return State.MAXED;
        }
        if (level > 0) {
            if (c == Check.OK) {
                return State.UPGRADABLE;
            }
            return State.LEARNED;
        }
        if (c == Check.LOCKED) {
            return State.LOCKED;
        }
        if (c == Check.OK) {
            return State.AVAILABLE;
        }
        return State.UNAFFORDABLE;
    }
}
