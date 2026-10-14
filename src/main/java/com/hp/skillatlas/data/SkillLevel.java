package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class SkillLevel {
    public static final int MAX_REWARDS = 32;

    public int cost = 1;
    public final List<Reward> rewards = new ArrayList<>();

    public SkillLevel() {
    }

    public SkillLevel(int cost, Reward... rewards) {
        this.cost = cost;
        this.rewards.addAll(List.of(rewards));
    }

    public SkillLevel copy() {
        SkillLevel l = new SkillLevel();
        l.cost = cost;
        for (Reward r : rewards) {
            l.rewards.add(r.copy());
        }
        return l;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("cost", cost);
        JsonArray a = new JsonArray();
        for (Reward r : rewards) {
            a.add(r.toJson());
        }
        o.add("rewards", a);
        return o;
    }

    public static SkillLevel fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "level");
        SkillLevel l = new SkillLevel();
        l.cost = Json.integer(o, "cost", 1, 0, 1_000_000_000);
        l.rewards.addAll(Json.list(o, "rewards", MAX_REWARDS, Reward::fromJson));
        return l;
    }
}
