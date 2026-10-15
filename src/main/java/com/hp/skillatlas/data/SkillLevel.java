package com.hp.skillatlas.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class SkillLevel {
    public static final int MAX_REWARDS = 32;
    public static final int MAX_ITEMS = 16;

    public int cost = 1;
    public Boolean experience = false;
    public final List<Reward> rewards = new ArrayList<>();
    public final List<ItemCost> items = new ArrayList<>();

    public SkillLevel() {
    }

    public SkillLevel(int cost, Reward... rewards) {
        this.cost = cost;
        this.rewards.addAll(List.of(rewards));
    }

    public SkillLevel copy() {
        SkillLevel l = new SkillLevel();
        l.cost = cost;
        l.experience = experience;
        for (Reward r : rewards) {
            l.rewards.add(r.copy());
        }
        for (ItemCost c : items) {
            l.items.add(c.copy());
        }
        return l;
    }

    public boolean usesExperience(boolean fallback) {
        return experience == null ? fallback : experience;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("cost", cost);
        if (experience != null) {
            o.addProperty("experience", experience);
        }
        JsonArray a = new JsonArray();
        for (Reward r : rewards) {
            a.add(r.toJson());
        }
        o.add("rewards", a);
        if (!items.isEmpty()) {
            JsonArray it = new JsonArray();
            for (ItemCost c : items) {
                it.add(c.toJson());
            }
            o.add("items", it);
        }
        return o;
    }

    public static SkillLevel fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "level");
        SkillLevel l = new SkillLevel();
        l.cost = Json.integer(o, "cost", 1, 0, 1_000_000_000);
        l.experience = o.has("experience") && !o.get("experience").isJsonNull() ? Json.bool(o, "experience", false) : null;
        l.rewards.addAll(Json.list(o, "rewards", MAX_REWARDS, Reward::fromJson));
        l.items.addAll(Json.list(o, "items", MAX_ITEMS, ItemCost::fromJson));
        return l;
    }
}
