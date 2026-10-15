package com.hp.skillatlas.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Reward {
    public RewardType type = RewardType.ATTRIBUTE;
    public String target = "minecraft:attack_damage";
    public double amount = 1.0D;
    public RewardOperation operation = RewardOperation.ADD;
    public int amplifier;
    public int duration = 60;
    public int interval = 20;
    public boolean particles;
    public boolean icon = true;

    public static Reward attribute(String target, double amount, RewardOperation operation) {
        Reward r = new Reward();
        r.type = RewardType.ATTRIBUTE;
        r.target = target;
        r.amount = amount;
        r.operation = operation;
        return r;
    }

    public static Reward effect(String target, int amplifier, int duration, int interval) {
        Reward r = new Reward();
        r.type = RewardType.EFFECT;
        r.target = target;
        r.amplifier = amplifier;
        r.duration = duration;
        r.interval = interval;
        return r;
    }

    public static Reward immunity(String target) {
        Reward r = new Reward();
        r.type = RewardType.IMMUNITY;
        r.target = target;
        return r;
    }

    public Reward copy() {
        Reward r = new Reward();
        r.type = type;
        r.target = target;
        r.amount = amount;
        r.operation = operation;
        r.amplifier = amplifier;
        r.duration = duration;
        r.interval = interval;
        r.particles = particles;
        r.icon = icon;
        return r;
    }

    public void switchType(RewardType next) {
        if (next == type) {
            return;
        }
        if (next == RewardType.COMMAND) {
            target = "";
        } else if (type == RewardType.COMMAND || next == RewardType.ATTRIBUTE || type == RewardType.ATTRIBUTE) {
            if (next == RewardType.ATTRIBUTE) {
                target = "minecraft:attack_damage";
            } else {
                target = "minecraft:speed";
            }
        }
        type = next;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.key);
        switch (type) {
            case ATTRIBUTE -> {
                o.addProperty("target", target);
                o.addProperty("amount", amount);
                o.addProperty("operation", operation.key);
            }
            case EFFECT -> {
                o.addProperty("target", target);
                o.addProperty("amplifier", amplifier);
                o.addProperty("duration", duration);
                o.addProperty("interval", interval);
                o.addProperty("particles", particles);
                o.addProperty("icon", icon);
            }
            case IMMUNITY -> o.addProperty("target", target);
            case COMMAND -> o.addProperty("command", target);
        }
        return o;
    }

    public static Reward fromJson(JsonElement e) {
        JsonObject o = Json.object(e, "reward");
        Reward r = new Reward();
        r.type = RewardType.byKey(Json.string(o, "type", RewardType.ATTRIBUTE.key, 32));
        if (r.type == RewardType.COMMAND) {
            r.target = Json.string(o, "command", "", 1024).trim();
            if (r.target.startsWith("/")) {
                r.target = r.target.substring(1);
            }
            if (r.target.isEmpty()) {
                throw new AtlasException("skill_atlas.error.empty_command");
            }
        } else {
            r.target = Json.resource(Json.string(o, "target", "", 256), "target");
        }
        r.amount = Json.number(o, "amount", 1.0D, -1.0E6D, 1.0E6D);
        r.operation = RewardOperation.byKey(Json.string(o, "operation", RewardOperation.ADD.key, 32));
        r.amplifier = Json.integer(o, "amplifier", 0, 0, 255);
        r.duration = Json.integer(o, "duration", 60, 1, 1_000_000);
        r.interval = Json.integer(o, "interval", 20, 1, 72_000);
        r.particles = Json.bool(o, "particles", false);
        r.icon = Json.bool(o, "icon", true);
        return r;
    }
}
