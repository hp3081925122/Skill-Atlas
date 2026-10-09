package com.hp.skillatlas.core;

import com.hp.skillatlas.model.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class RewardPlan {
    public record Entry(UUID key, Definition.Reward reward) {}
    public static List<Entry> build(Definition d, PlayerProgress p) {
        List<Entry> result = new ArrayList<>();
        p.learned.forEach((id, learned) -> {
            Definition.Node n = d.nodes.get(id);
            if (n == null || learned.level < 1 || learned.level > n.levels.size()) return;
            var rewards = n.levels.get(learned.level - 1).rewards;
            for (int i = 0; i < rewards.size(); i++) result.add(new Entry(UUID.nameUUIDFromBytes(("skill_atlas:" + id + ":" + i).getBytes(StandardCharsets.UTF_8)), rewards.get(i)));
        });
        return result;
    }
}
