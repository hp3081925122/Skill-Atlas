package com.hp.skillatlas.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;

public final class Definition {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public int schemaVersion = 1;
    public long revision = 1;
    public Map<String, Pool> pools = new LinkedHashMap<>();
    public Map<String, Category> categories = new LinkedHashMap<>();
    public Map<String, Node> nodes = new LinkedHashMap<>();
    public List<Line> lines = new ArrayList<>();
    public Map<String, Rule> rules = new LinkedHashMap<>();

    public Definition copy() { return JSON.fromJson(JSON.toJson(this), Definition.class); }
    public static final class Pool {
        public String name = "";
        public long baseExperience = 100;
        public long experienceGrowth = 50;
        public long pointsPerLevel = 1;
    }
    public static final class Category {
        public String name = "";
        public String pool = "general";
        public int order;
    }
    public static final class Node {
        public String name = "";
        public String tooltip = "";
        public String aliases = "";
        public String category = "combat";
        public double x;
        public double y;
        public String iconType = "item";
        public String icon = "minecraft:book";
        public String iconNbt = "";
        public boolean hideUntilReady;
        public String prerequisiteMode = "all";
        public List<Requirement> prerequisites = new ArrayList<>();
        public List<Level> levels = new ArrayList<>(List.of(new Level()));
        public Node copy() { return JSON.fromJson(JSON.toJson(this), Node.class); }
    }
    public static final class Level {
        public String pool = "";
        public long cost = 1;
        public List<ItemCost> items = new ArrayList<>();
        public List<Reward> rewards = new ArrayList<>();
    }
    public static final class ItemCost {
        public String item = "minecraft:diamond";
        public int count = 1;
        public String nbt = "";
    }
    public static final class Reward {
        public String type = "attribute";
        public String target = "minecraft:generic.attack_damage";
        public double amount = 1;
        public String operation = "addition";
        public int amplifier;
        public int duration = 40;
        public int interval = 20;
        public boolean particles;
        public boolean showIcon = true;
        public List<String> effects = new ArrayList<>();
    }
    public static final class Requirement {
        public String node = "";
        public int level = 1;
        public Requirement() {}
        public Requirement(String node, int level) { this.node = node; this.level = level; }
    }
    public static final class Line {
        public String from = "";
        public String to = "";
        public boolean visible = true;
        public Line() {}
        public Line(String from, String to) { this.from = from; this.to = to; }
    }
    public static final class Rule {
        public String event = "kill";
        public String target = "minecraft:zombie";
        public String pool = "general";
        public String mode = "count";
        public long threshold = 10;
        public long amount = 1;
    }
    public static Definition defaults() {
        Definition d = new Definition();
        Pool p = new Pool(); p.name = "通用"; d.pools.put("general", p);
        Category c = new Category(); c.name = "战斗"; d.categories.put("combat", c);
        Category m = new Category(); m.name = "挖掘"; m.order = 1; d.categories.put("mining", m);
        Node n = new Node(); n.name = "力量"; n.tooltip = "提升攻击力";
        n.icon = "minecraft:iron_sword"; n.levels.get(0).rewards.add(new Reward());
        Level l = new Level(); l.cost = 2; Reward r = new Reward(); r.amount = 2; l.rewards.add(r); n.levels.add(l);
        d.nodes.put("strength", n);
        Node miner = new Node(); miner.name = "矿工"; miner.category = "mining"; miner.icon = "minecraft:iron_pickaxe";
        Reward haste = new Reward(); haste.type = "effect"; haste.target = "minecraft:haste"; miner.levels.get(0).rewards.add(haste);
        d.nodes.put("miner", miner);
        Rule kill = new Rule(); d.rules.put("zombies", kill);
        Rule mine = new Rule(); mine.event = "mine"; mine.target = "#forge:ores"; mine.threshold = 20; d.rules.put("ores", mine);
        return d;
    }
}
