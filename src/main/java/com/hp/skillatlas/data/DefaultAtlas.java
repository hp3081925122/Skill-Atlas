package com.hp.skillatlas.data;

public final class DefaultAtlas {
    private static final int UNIT = 40;
    private static final int[][] SHAPE = {{1, 2}, {1, 4}, {2, 6}, {3, 8}, {1, 9}, {5, 7}, {4, 10}, {6, 9}, {8, 10}, {2, 11}, {1, 13}};
    private static final int[] PARENT = {-1, 0, 1, 2, 3, 3, 3, 6, 7, 6, 9};
    private static final int MID = 3;
    private static final int SIDE = 8;
    private static final int PEAK = 10;

    private DefaultAtlas() {
    }

    public static AtlasDefinition create() {
        AtlasDefinition d = new AtlasDefinition();
        PointPool combat = new PointPool("combat", "skill_atlas.sample.pool.combat", 0xE0685A, 1);
        combat.method = RuleType.KILL;
        combat.hostile = true;
        combat.count = 10;
        d.pools.put(combat.id, combat);
        PointPool mining = new PointPool("mining", "skill_atlas.sample.pool.mining", 0x6FB7E8, 1);
        mining.method = RuleType.MINE;
        mining.targets.add("#forge:ores");
        mining.count = 8;
        d.pools.put(mining.id, mining);
        d.categories.put("combat", new Category("combat", "skill_atlas.sample.category.combat", Icon.item("minecraft:iron_sword"), "combat"));
        d.categories.put("mining", new Category("mining", "skill_atlas.sample.category.mining", Icon.item("minecraft:iron_pickaxe"), "mining"));

        branch(d, "health", "minecraft:apple", "minecraft:generic.max_health", RewardOperation.ADD, new double[]{1.0D, 4.0D, 6.0D}, -1, -1, false,
                new ItemCost("minecraft:golden_carrot", 8, ""), new ItemCost("minecraft:golden_apple", 2, ""), new ItemCost("minecraft:heart_of_the_sea", 1, ""));
        branch(d, "healing", "minecraft:glistering_melon_slice", "skill_atlas:healing_efficiency", RewardOperation.ADD, new double[]{2.5D, 10.0D, 15.0D}, 1, -1, false,
                new ItemCost("minecraft:glistering_melon_slice", 6, ""), new ItemCost("minecraft:ghast_tear", 2, ""), new ItemCost("minecraft:totem_of_undying", 1, ""));
        branch(d, "attack", "minecraft:iron_sword", "minecraft:generic.attack_damage", RewardOperation.ADD, new double[]{0.2D, 0.8D, 1.2D}, -1, -1, true,
                new ItemCost("minecraft:iron_ingot", 12, ""), new ItemCost("minecraft:blaze_rod", 8, ""), new ItemCost("minecraft:diamond", 4, ""));
        branch(d, "attack_speed", "minecraft:golden_sword", "minecraft:generic.attack_speed", RewardOperation.MULTIPLY_BASE, new double[]{0.02D, 0.08D, 0.12D}, -1, 1, true,
                new ItemCost("minecraft:feather", 16, ""), new ItemCost("minecraft:gold_ingot", 12, ""), new ItemCost("minecraft:phantom_membrane", 4, ""));
        branch(d, "armor", "minecraft:iron_chestplate", "minecraft:generic.armor", RewardOperation.ADD, new double[]{0.5D, 2.0D, 3.0D}, 1, -1, true,
                new ItemCost("minecraft:leather", 16, ""), new ItemCost("minecraft:iron_block", 3, ""), new ItemCost("minecraft:diamond", 4, ""));
        branch(d, "toughness", "minecraft:netherite_chestplate", "minecraft:generic.armor_toughness", RewardOperation.ADD, new double[]{0.25D, 1.0D, 1.5D}, 1, 1, true,
                new ItemCost("minecraft:obsidian", 8, ""), new ItemCost("minecraft:shulker_shell", 2, ""), new ItemCost("minecraft:netherite_scrap", 2, ""));
        branch(d, "experience", "minecraft:experience_bottle", "skill_atlas:experience_gain", RewardOperation.ADD, new double[]{5.0D, 20.0D, 30.0D}, -1, 1, false,
                new ItemCost("minecraft:lapis_lazuli", 16, ""), new ItemCost("minecraft:ender_pearl", 8, ""), new ItemCost("minecraft:experience_bottle", 8, ""));
        branch(d, "looting", "minecraft:rabbit_foot", "skill_atlas:looting", RewardOperation.ADD, new double[]{0.125D, 0.5D, 0.75D}, 1, 1, false,
                new ItemCost("minecraft:bone", 24, ""), new ItemCost("minecraft:rabbit_foot", 2, ""), new ItemCost("minecraft:emerald", 16, ""));

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
        luck.levels.get(0).items.add(new ItemCost("minecraft:emerald", 4, ""));
        d.nodes.put(luck.id, luck);
        return d;
    }

    private static void branch(AtlasDefinition d, String id, String icon, String attribute, RewardOperation op, double[] amounts, int sx, int sy, boolean swap, ItemCost mid, ItemCost side, ItemCost peak) {
        for (int i = 0; i < SHAPE.length; i++) {
            int x = sx * SHAPE[i][0] * UNIT;
            int y = sy * SHAPE[i][1] * UNIT;
            if (swap) {
                x = sx * SHAPE[i][1] * UNIT;
                y = sy * SHAPE[i][0] * UNIT;
            }
            SkillNode n = node(id + "_" + (i + 1), "combat", icon, x, y);
            n.name = "skill_atlas.sample.node." + id;
            n.tooltip = "skill_atlas.sample.node." + id + ".tooltip";
            int cost = 1;
            double amount = amounts[0];
            ItemCost item = null;
            if (i == MID) {
                cost = 3;
                amount = amounts[1];
                item = mid;
                n.name = n.name + ".mid";
            } else if (i == SIDE) {
                cost = 4;
                amount = amounts[2];
                item = side;
                n.name = n.name + ".side";
            } else if (i == PEAK) {
                cost = 4;
                amount = amounts[2];
                item = peak;
                n.name = n.name + ".peak";
            }
            SkillLevel level = new SkillLevel(cost, Reward.attribute(attribute, amount, op));
            if (item != null) {
                n.major = true;
                level.items.add(item);
            }
            n.levels.add(level);
            if (PARENT[i] >= 0) {
                n.prerequisites.add(new Prerequisite(id + "_" + (PARENT[i] + 1), true));
            }
            d.nodes.put(n.id, n);
        }
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
