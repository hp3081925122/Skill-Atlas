package com.hp.skillatlas.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class AtlasLogic {
    public enum State {
        LOCKED, UNAFFORDABLE, AVAILABLE, LEARNED, UPGRADABLE, MAXED
    }

    public enum Check {
        OK, MISSING, MAXED, LOCKED, EXCLUDED, NO_POINTS, NO_EXPERIENCE, NO_ITEMS
    }

    public enum Refund {
        OK, MISSING, DISABLED, NOT_LEARNED, DEPENDENT, NO_EXPERIENCE
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
            if (pre.met(p)) {
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

    public static List<SkillNode> choices(AtlasDefinition d, SkillNode n) {
        List<SkillNode> out = new ArrayList<>();
        if (n.choiceGroup.isEmpty()) {
            return out;
        }
        for (SkillNode o : d.nodes.values()) {
            if (o != n && !o.id.equals(n.id) && n.choiceGroup.equals(o.choiceGroup)) {
                out.add(o);
            }
        }
        return out;
    }

    public static SkillNode excludedBy(AtlasDefinition d, SkillNode n, PlayerProgress p) {
        if (n.choiceGroup.isEmpty() || p.level(n.id) > 0) {
            return null;
        }
        for (SkillNode o : d.nodes.values()) {
            if (o != n && !o.id.equals(n.id) && n.choiceGroup.equals(o.choiceGroup) && p.level(o.id) > 0) {
                return o;
            }
        }
        return null;
    }

    public static boolean visible(AtlasDefinition d, SkillNode n, PlayerProgress p) {
        if (!n.hidden || p.level(n.id) > 0) {
            return true;
        }
        return prerequisitesMet(d, n, p);
    }

    public static Check check(AtlasDefinition d, SkillNode n, PlayerProgress p, Predicate<List<ItemCost>> items) {
        return check(d, n, p, items, 0);
    }

    public static Check check(AtlasDefinition d, SkillNode n, PlayerProgress p, Predicate<List<ItemCost>> items, long experience) {
        if (n == null) {
            return Check.MISSING;
        }
        int level = p.level(n.id);
        if (level >= n.maxLevel()) {
            return Check.MAXED;
        }
        if (excludedBy(d, n, p) != null) {
            return Check.EXCLUDED;
        }
        if (!prerequisitesMet(d, n, p)) {
            return Check.LOCKED;
        }
        SkillLevel next = n.levels.get(level);
        if (d.usesExperience(n, next)) {
            if (experience < next.cost) {
                return Check.NO_EXPERIENCE;
            }
        } else if (p.points(d.poolOf(n)) < next.cost) {
            return Check.NO_POINTS;
        }
        if (!next.items.isEmpty() && !items.test(next.items)) {
            return Check.NO_ITEMS;
        }
        return Check.OK;
    }

    public static SkillNode refundBlocker(AtlasDefinition d, SkillNode n, PlayerProgress p) {
        int after = p.level(n.id) - 1;
        for (SkillNode m : d.nodes.values()) {
            if (m == n || m.id.equals(n.id) || p.level(m.id) <= 0) {
                continue;
            }
            int total = 0;
            int met = 0;
            boolean depends = false;
            for (Prerequisite pre : m.prerequisites) {
                if (!d.nodes.containsKey(pre.node)) {
                    continue;
                }
                total++;
                int level = p.level(pre.node);
                if (pre.node.equals(n.id)) {
                    depends = true;
                    level = after;
                }
                if (level > pre.level) {
                    met++;
                }
            }
            if (!depends) {
                continue;
            }
            if (m.requireAll ? met < total : met == 0) {
                return m;
            }
        }
        return null;
    }

    public static Refund refundCheck(AtlasDefinition d, SkillNode n, PlayerProgress p, boolean enabled, long cost, long experience) {
        if (n == null) {
            return Refund.MISSING;
        }
        if (!enabled) {
            return Refund.DISABLED;
        }
        if (p.level(n.id) <= 0) {
            return Refund.NOT_LEARNED;
        }
        if (refundBlocker(d, n, p) != null) {
            return Refund.DEPENDENT;
        }
        if (experience < cost) {
            return Refund.NO_EXPERIENCE;
        }
        return Refund.OK;
    }

    public static State state(AtlasDefinition d, SkillNode n, PlayerProgress p, Predicate<List<ItemCost>> items) {
        return state(d, n, p, items, 0);
    }

    public static State state(AtlasDefinition d, SkillNode n, PlayerProgress p, Predicate<List<ItemCost>> items, long experience) {
        int level = p.level(n.id);
        Check c = check(d, n, p, items, experience);
        if (c == Check.MAXED) {
            return State.MAXED;
        }
        if (level > 0) {
            if (c == Check.OK) {
                return State.UPGRADABLE;
            }
            return State.LEARNED;
        }
        if (c == Check.LOCKED || c == Check.EXCLUDED) {
            return State.LOCKED;
        }
        if (c == Check.OK) {
            return State.AVAILABLE;
        }
        return State.UNAFFORDABLE;
    }
}
