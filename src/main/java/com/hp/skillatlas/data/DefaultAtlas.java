package com.hp.skillatlas.data;

public final class DefaultAtlas {
    private DefaultAtlas() {
    }

    public static AtlasDefinition create() {
        AtlasDefinition d = new AtlasDefinition();
        d.pools.put("combat", new PointPool("combat", "skill_atlas.sample.pool.combat", 0xE0685A, 1));
        d.pools.put("mining", new PointPool("mining", "skill_atlas.sample.pool.mining", 0x6FB7E8, 1));
        d.categories.put("combat", new Category("combat", "skill_atlas.sample.category.combat", Icon.item("minecraft:iron_sword"), "combat"));
        d.categories.put("mining", new Category("mining", "skill_atlas.sample.category.mining", Icon.item("minecraft:iron_pickaxe"), "mining"));

        PointRule kill = new PointRule();
        kill.id = "kill_monsters";
        kill.name = "skill_atlas.sample.rule.kill";
        kill.type = RuleType.KILL;
        kill.count = 10;
        kill.points = 1;
        kill.pool = "combat";
        d.rules.put(kill.id, kill);

        PointRule mine = new PointRule();
        mine.id = "mine_ores";
        mine.name = "skill_atlas.sample.rule.mine";
        mine.type = RuleType.MINE;
        mine.targets.add("#forge:ores");
        mine.count = 8;
        mine.points = 1;
        mine.pool = "mining";
        d.rules.put(mine.id, mine);

        SkillNode strength = node("strength", "combat", "minecraft:iron_sword", 0, 0);
        strength.levels.add(new SkillLevel(1, Reward.attribute("minecraft:generic.attack_damage", 1.0D, RewardOperation.ADD)));
        strength.levels.add(new SkillLevel(2, Reward.attribute("minecraft:generic.attack_damage", 2.0D, RewardOperation.ADD)));
        strength.levels.add(new SkillLevel(3, Reward.attribute("minecraft:generic.attack_damage", 3.0D, RewardOperation.ADD)));
        d.nodes.put(strength.id, strength);

        SkillNode vitality = node("vitality", "combat", "minecraft:golden_apple", 64, -32);
        vitality.prerequisites.add(new Prerequisite("strength", true));
        vitality.levels.add(new SkillLevel(1, Reward.attribute("minecraft:generic.max_health", 2.0D, RewardOperation.ADD)));
        vitality.levels.add(new SkillLevel(2, Reward.attribute("minecraft:generic.max_health", 4.0D, RewardOperation.ADD)));
        d.nodes.put(vitality.id, vitality);

        SkillNode swiftness = node("swiftness", "combat", "minecraft:sugar", 64, 32);
        swiftness.prerequisites.add(new Prerequisite("strength", true));
        swiftness.levels.add(new SkillLevel(2, Reward.effect("minecraft:speed", 0, 60, 20)));
        d.nodes.put(swiftness.id, swiftness);

        SkillNode resilience = node("resilience", "combat", "minecraft:totem_of_undying", 136, 0);
        resilience.major = true;
        resilience.hidden = true;
        resilience.requireAll = false;
        resilience.prerequisites.add(new Prerequisite("vitality", true));
        resilience.prerequisites.add(new Prerequisite("swiftness", true));
        resilience.levels.add(new SkillLevel(3, Reward.immunity("minecraft:poison")));
        d.nodes.put(resilience.id, resilience);

        SkillNode haste = node("haste", "mining", "minecraft:golden_pickaxe", 0, 0);
        haste.levels.add(new SkillLevel(1, Reward.effect("minecraft:haste", 0, 60, 20)));
        haste.levels.add(new SkillLevel(2, Reward.effect("minecraft:haste", 1, 60, 20)));
        d.nodes.put(haste.id, haste);

        SkillNode vision = node("night_vision", "mining", "minecraft:ender_eye", 64, -32);
        vision.prerequisites.add(new Prerequisite("haste", true));
        vision.levels.add(new SkillLevel(1, Reward.effect("minecraft:night_vision", 0, 400, 100)));
        d.nodes.put(vision.id, vision);

        SkillNode luck = node("fortune", "mining", "minecraft:emerald", 64, 32);
        luck.prerequisites.add(new Prerequisite("haste", true));
        luck.levels.add(new SkillLevel(2, Reward.attribute("minecraft:generic.luck", 1.0D, RewardOperation.ADD)));
        d.nodes.put(luck.id, luck);
        return d;
    }

    private static SkillNode node(String id, String category, String icon, int x, int y) {
        SkillNode n = new SkillNode();
        n.id = id;
        n.category = category;
        n.name = "skill_atlas.sample.node." + id;
        n.tooltip = "skill_atlas.sample.node." + id + ".tooltip";
        n.icon = Icon.item(icon);
        n.x = x;
        n.y = y;
        return n;
    }
}
