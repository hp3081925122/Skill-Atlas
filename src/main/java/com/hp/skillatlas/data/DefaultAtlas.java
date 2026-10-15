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
        mining.targets.add("#c:ores");
        mining.count = 8;
        d.pools.put(mining.id, mining);
        d.categories.put("combat", new Category("combat", "skill_atlas.sample.category.combat", Icon.item("minecraft:iron_sword"), "combat"));
        d.categories.put("mining", new Category("mining", "skill_atlas.sample.category.mining", Icon.item("minecraft:iron_pickaxe"), "mining"));

        branch(d, "health", "minecraft:apple", "minecraft:max_health", RewardOperation.ADD, new double[]{1.0D, 4.0D, 6.0D}, -1, -1, false,
                new ItemCost("minecraft:golden_carrot", 8, ""), new ItemCost("minecraft:golden_apple", 2, ""), new ItemCost("minecraft:heart_of_the_sea", 1, ""));
        branch(d, "healing", "minecraft:glistering_melon_slice", "skill_atlas:healing_efficiency", RewardOperation.ADD, new double[]{2.5D, 10.0D, 15.0D}, 1, -1, false,
                new ItemCost("minecraft:glistering_melon_slice", 6, ""), new ItemCost("minecraft:ghast_tear", 2, ""), new ItemCost("minecraft:totem_of_undying", 1, ""));
        branch(d, "attack", "minecraft:iron_sword", "minecraft:attack_damage", RewardOperation.ADD, new double[]{0.2D, 0.8D, 1.2D}, -1, -1, true,
                new ItemCost("minecraft:iron_ingot", 12, ""), new ItemCost("minecraft:blaze_rod", 8, ""), new ItemCost("minecraft:diamond", 4, ""));
        branch(d, "attack_speed", "minecraft:golden_sword", "minecraft:attack_speed", RewardOperation.MULTIPLY_BASE, new double[]{0.02D, 0.08D, 0.12D}, -1, 1, true,
                new ItemCost("minecraft:feather", 16, ""), new ItemCost("minecraft:gold_ingot", 12, ""), new ItemCost("minecraft:phantom_membrane", 4, ""));
        branch(d, "armor", "minecraft:iron_chestplate", "minecraft:armor", RewardOperation.ADD, new double[]{0.5D, 2.0D, 3.0D}, 1, -1, true,
                new ItemCost("minecraft:leather", 16, ""), new ItemCost("minecraft:iron_block", 3, ""), new ItemCost("minecraft:diamond", 4, ""));
        branch(d, "toughness", "minecraft:netherite_chestplate", "minecraft:armor_toughness", RewardOperation.ADD, new double[]{0.25D, 1.0D, 1.5D}, 1, 1, true,
                new ItemCost("minecraft:obsidian", 8, ""), new ItemCost("minecraft:shulker_shell", 2, ""), new ItemCost("minecraft:netherite_scrap", 2, ""));
        branch(d, "experience", "minecraft:experience_bottle", "skill_atlas:experience_gain", RewardOperation.ADD, new double[]{5.0D, 20.0D, 30.0D}, -1, 1, false,
                new ItemCost("minecraft:lapis_lazuli", 16, ""), new ItemCost("minecraft:ender_pearl", 8, ""), new ItemCost("minecraft:experience_bottle", 8, ""));
        branch(d, "looting", "minecraft:rabbit_foot", "skill_atlas:looting", RewardOperation.ADD, new double[]{0.125D, 0.5D, 0.75D}, 1, 1, false,
                new ItemCost("minecraft:bone", 24, ""), new ItemCost("minecraft:rabbit_foot", 2, ""), new ItemCost("minecraft:emerald", 16, ""));

        SkillNode haste = node("haste", "mining", "minecraft:golden_pickaxe", 0, 0);
        for (int i = 1; i <= 10; i++) {
            haste.levels.add(new SkillLevel(i, Reward.attribute("skill_atlas:mining_speed", i * 10.0D, RewardOperation.ADD)));
        }
        d.nodes.put(haste.id, haste);

        SkillNode vision = node("night_vision", "mining", "minecraft:ender_eye", -64, -48);
        vision.prerequisites.add(new Prerequisite("haste", true));
        vision.levels.add(new SkillLevel(4, Reward.effect("minecraft:night_vision", 0, 400, 100)));
        vision.levels.get(0).items.add(new ItemCost("minecraft:golden_carrot", 32, ""));
        d.nodes.put(vision.id, vision);

        SkillNode luck = node("fortune", "mining", "minecraft:emerald", 64, -48);
        luck.prerequisites.add(new Prerequisite("haste", true));
        luck.levels.add(new SkillLevel(4, Reward.attribute("skill_atlas:fortune", 1.0D, RewardOperation.ADD)));
        luck.levels.add(new SkillLevel(8, Reward.attribute("skill_atlas:fortune", 2.0D, RewardOperation.ADD)));
        luck.levels.add(new SkillLevel(12, Reward.attribute("skill_atlas:fortune", 3.0D, RewardOperation.ADD)));
        luck.levels.get(0).items.add(new ItemCost("minecraft:emerald", 16, ""));
        luck.levels.get(1).items.add(new ItemCost("minecraft:diamond", 4, ""));
        luck.levels.get(2).items.add(new ItemCost("minecraft:nether_star", 1, ""));
        d.nodes.put(luck.id, luck);

        SkillNode minerals = node("stone_minerals", "mining", "minecraft:diamond", -64, 48);
        minerals.prerequisites.add(new Prerequisite("haste", true));
        SkillNode experience = node("mining_experience", "mining", "minecraft:experience_bottle", 64, 48);
        experience.prerequisites.add(new Prerequisite("haste", true));
        for (int i = 1; i <= 5; i++) {
            minerals.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:stone_mineral_chance", i * 2.0D, RewardOperation.ADD)));
            experience.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:mining_experience", i * 20.0D, RewardOperation.ADD)));
        }
        d.nodes.put(minerals.id, minerals);
        d.nodes.put(experience.id, experience);

        PointPool farming = new PointPool("farming", "skill_atlas.sample.pool.farming", 0x8EDB7A, 0);
        farming.method = RuleType.HARVEST;
        farming.count = 16;
        d.pools.put(farming.id, farming);
        PointPool fishing = new PointPool("fishing", "skill_atlas.sample.pool.fishing", 0x6FB7E8, 0);
        fishing.method = RuleType.FISH;
        fishing.count = 4;
        d.pools.put(fishing.id, fishing);
        d.categories.put("farming", new Category("farming", "skill_atlas.sample.category.farming", Icon.item("minecraft:wheat"), "farming"));
        d.categories.put("fishing", new Category("fishing", "skill_atlas.sample.category.fishing", Icon.item("minecraft:fishing_rod"), "fishing"));
        d.categories.put("enchanting", new Category("enchanting", "skill_atlas.sample.category.enchanting", Icon.item("minecraft:enchanting_table"), ""));

        int[][] farmingPositions = {{0, 96}, {0, 32}, {0, -32}, {-64, -96}, {64, -96}};
        for (int i = 1; i <= 5; i++) {
            SkillNode harvest = node("harvest_experience_" + i, "farming", "minecraft:wheat", farmingPositions[i - 1][0], farmingPositions[i - 1][1]);
            harvest.name = "skill_atlas.sample.node.harvest_experience";
            harvest.tooltip = "skill_atlas.sample.node.harvest_experience.tooltip";
            if (i > 1) {
                harvest.prerequisites.add(new Prerequisite("harvest_experience_" + (i == 5 ? 3 : i - 1), true));
            }
            harvest.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:harvest_experience", 1, RewardOperation.ADD)));
            d.nodes.put(harvest.id, harvest);
        }
        SkillNode replant = node("crop_replanting", "farming", "minecraft:wheat_seeds", -96, -176);
        replant.size = 1.3D;
        replant.prerequisites.add(new Prerequisite("harvest_experience_4", true));
        replant.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:crop_replanting", 1, RewardOperation.ADD)));
        replant.levels.get(0).items.add(new ItemCost("minecraft:golden_hoe", 1, ""));
        d.nodes.put(replant.id, replant);

        SkillNode yield = node("harvest_yield", "farming", "minecraft:hay_block", 96, -176);
        yield.size = 1.3D;
        yield.prerequisites.add(new Prerequisite("harvest_experience_5", true));
        yield.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:harvest_yield", 30, RewardOperation.ADD)));
        yield.levels.get(0).items.add(new ItemCost("minecraft:hay_block", 1, ""));
        d.nodes.put(yield.id, yield);
        SkillNode lure = node("fishing_lure", "fishing", "minecraft:fishing_rod", -48, 0);
        SkillNode fishingLuck = node("fishing_luck", "fishing", "minecraft:nautilus_shell", 48, 0);
        for (int i = 1; i <= 3; i++) {
            lure.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:fishing_lure", i, RewardOperation.ADD)));
            fishingLuck.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:fishing_luck", i, RewardOperation.ADD)));
        }
        d.nodes.put(lure.id, lure);
        d.nodes.put(fishingLuck.id, fishingLuck);
        for (int i = 1; i <= 2; i++) {
            SkillNode luckMajor = node("fishing_luck_major_" + i, "fishing", "minecraft:emerald", i == 1 ? -48 : 48, 64);
            luckMajor.name = "skill_atlas.sample.node.fishing_luck_major";
            luckMajor.tooltip = "skill_atlas.sample.node.fishing_luck_major.tooltip";
            luckMajor.size = 1.3D;
            luckMajor.prerequisites.add(new Prerequisite(i == 1 ? "fishing_lure" : "fishing_luck", true));
            luckMajor.levels.add(new SkillLevel(1, Reward.attribute("minecraft:luck", 1, RewardOperation.ADD)));
            luckMajor.levels.get(0).items.add(new ItemCost("minecraft:emerald", 16, ""));
            d.nodes.put(luckMajor.id, luckMajor);
        }
        SkillNode enchanting = node("enchanting_cap", "enchanting", "minecraft:enchanted_book", 0, 0);
        for (int i = 1; i <= 30; i++) {
            SkillLevel level = new SkillLevel(10 * i, Reward.attribute("skill_atlas:enchanting_cap", i, RewardOperation.ADD));
            level.experience = true;
            enchanting.levels.add(level);
        }
        d.nodes.put(enchanting.id, enchanting);

        String[] enchantingIds = {"lapis_saving", "enchanting_refund", "book_copy", "enchanting_insight"};
        String[] enchantingIcons = {"minecraft:lapis_lazuli", "minecraft:experience_bottle", "minecraft:book", "minecraft:ender_eye"};
        double[] enchantingAmounts = {30, 30, 15, 1};
        int[][] enchantingPositions = {{-96, -64}, {0, -96}, {96, -64}, {0, 80}};
        for (int i = 0; i < enchantingIds.length; i++) {
            SkillNode major = node(enchantingIds[i], "enchanting", enchantingIcons[i], enchantingPositions[i][0], enchantingPositions[i][1]);
            major.size = 1.3D;
            major.prerequisites.add(new Prerequisite("enchanting_cap", true));
            SkillLevel level = new SkillLevel(100, Reward.attribute("skill_atlas:" + enchantingIds[i], enchantingAmounts[i], RewardOperation.ADD));
            level.experience = true;
            major.levels.add(level);
            d.nodes.put(major.id, major);
        }

        PointPool running = new PointPool("running", "skill_atlas.sample.pool.running", 0x7FD8C8, 0);
        running.method = RuleType.MOVEMENT;
        running.count = 100;
        d.pools.put(running.id, running);
        d.categories.put("running", new Category("running", "skill_atlas.sample.category.running", Icon.item("minecraft:leather_boots"), "running"));
        movementBranch(d, "running_speed", "minecraft:sugar", "minecraft:movement_speed", RewardOperation.MULTIPLY_BASE, -96);
        movementBranch(d, "jump_height", "minecraft:rabbit_foot", "skill_atlas:jump_height", RewardOperation.ADD, 0);
        movementBranch(d, "swim_speed", "minecraft:prismarine_shard", "neoforge:swim_speed", RewardOperation.MULTIPLY_BASE, 96);

        SkillNode step = node("running_vitality", "running", "minecraft:diamond_boots", -96, 224);
        step.size = 1.3D;
        step.prerequisites.add(new Prerequisite("running_speed_5", true));
        step.levels.add(new SkillLevel(1, Reward.attribute("minecraft:step_height", 1.0D, RewardOperation.ADD)));
        step.levels.get(0).items.add(new ItemCost("minecraft:diamond_boots", 1, ""));
        d.nodes.put(step.id, step);

        SkillNode landing = node("light_landing", "running", "minecraft:feather", 0, 224);
        landing.size = 1.3D;
        landing.prerequisites.add(new Prerequisite("jump_height_5", true));
        landing.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:fall_damage_reduction", 40.0D, RewardOperation.ADD)));
        landing.levels.get(0).items.add(new ItemCost("minecraft:feather", 1, ""));
        d.nodes.put(landing.id, landing);

        SkillNode breathing = node("underwater_breathing", "running", "minecraft:turtle_helmet", 96, 224);
        breathing.size = 1.3D;
        breathing.prerequisites.add(new Prerequisite("swim_speed_5", true));
        breathing.levels.add(new SkillLevel(1, Reward.effect("minecraft:water_breathing", 0, 240, 20)));
        breathing.levels.get(0).items.add(new ItemCost("minecraft:turtle_helmet", 1, ""));
        d.nodes.put(breathing.id, breathing);

        SkillNode elytra = node("elytra_speed", "running", "minecraft:elytra", 0, 320);
        elytra.size = 1.3D;
        elytra.prerequisites.add(new Prerequisite("running_vitality", true));
        elytra.prerequisites.add(new Prerequisite("light_landing", true));
        elytra.prerequisites.add(new Prerequisite("underwater_breathing", true));
        elytra.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:elytra_speed", 50, RewardOperation.ADD)));
        elytra.levels.get(0).items.add(new ItemCost("minecraft:elytra", 1, ""));
        d.nodes.put(elytra.id, elytra);

        PointPool smelting = new PointPool("smelting", "skill_atlas.sample.pool.smelting", 0xE9A255, 0);
        smelting.method = RuleType.SMELT;
        smelting.count = 8;
        d.pools.put(smelting.id, smelting);
        d.categories.put("smelting", new Category("smelting", "skill_atlas.sample.category.smelting", Icon.item("minecraft:furnace"), "smelting"));
        for (int i = 1; i <= 15; i++) {
            int column = (i - 1) / 5;
            int row = (i - 1) % 5;
            SkillNode n = node("smelting_speed_" + i, "smelting", "minecraft:blaze_powder", (column - 1) * 96,
                    (column % 2 == 0 ? row : 4 - row) * 40);
            n.name = "skill_atlas.sample.node.smelting_speed";
            n.tooltip = "skill_atlas.sample.node.smelting_speed.tooltip";
            if (i > 1) {
                n.prerequisites.add(new Prerequisite("smelting_speed_" + (i - 1), true));
            }
            n.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:smelting_speed", 10.0D, RewardOperation.ADD)));
            d.nodes.put(n.id, n);
        }
        SkillNode doubling = node("raw_ore_doubling", "smelting", "minecraft:ancient_debris", -96, 224);
        doubling.size = 1.3D;
        doubling.prerequisites.add(new Prerequisite("smelting_speed_5", true));
        doubling.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:raw_ore_double_chance", 30.0D, RewardOperation.ADD)));
        doubling.levels.get(0).items.add(new ItemCost("minecraft:ancient_debris", 1, ""));
        d.nodes.put(doubling.id, doubling);

        SkillNode efficiency = node("fuel_efficiency", "smelting", "minecraft:coal_block", 0, -64);
        efficiency.size = 1.3D;
        efficiency.prerequisites.add(new Prerequisite("smelting_speed_10", true));
        efficiency.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:fuel_efficiency", 50.0D, RewardOperation.ADD)));
        efficiency.levels.get(0).items.add(new ItemCost("minecraft:coal_block", 1, ""));
        d.nodes.put(efficiency.id, efficiency);

        SkillNode smeltingExperience = node("smelting_experience", "smelting", "minecraft:experience_bottle", 96, 224);
        smeltingExperience.size = 1.3D;
        smeltingExperience.prerequisites.add(new Prerequisite("smelting_speed_15", true));
        smeltingExperience.levels.add(new SkillLevel(1, Reward.attribute("skill_atlas:smelting_experience", 100.0D, RewardOperation.ADD)));
        smeltingExperience.levels.get(0).items.add(new ItemCost("minecraft:experience_bottle", 1, ""));
        d.nodes.put(smeltingExperience.id, smeltingExperience);
        return d;
    }

    private static void movementBranch(AtlasDefinition d, String id, String icon, String attribute, RewardOperation operation, int x) {
        for (int i = 1; i <= 5; i++) {
            SkillNode n = node(id + "_" + i, "running", icon, x, (i - 1) * 40);
            n.name = "skill_atlas.sample.node." + id;
            n.tooltip = "skill_atlas.sample.node." + id + ".tooltip";
            if (i > 1) {
                n.prerequisites.add(new Prerequisite(id + "_" + (i - 1), true));
            }
            n.levels.add(new SkillLevel(1, Reward.attribute(attribute, 0.2D, operation)));
            d.nodes.put(n.id, n);
        }
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
                n.size = 1.3D;
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
