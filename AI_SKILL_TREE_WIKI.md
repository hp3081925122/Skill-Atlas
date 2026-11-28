# Skill Atlas — AI & Modpack Author Wiki (v1.4.0)

> **For AI Assistants:** Read this document completely before generating any `atlas.json` file or KubeJS script for **Skill Atlas**. Follow every schema rule, ID constraint, version-specific attribute ID, and validation rule strictly so the generated skill tree loads cleanly with `/skillatlas reload` on the first try.

---

## 1. Quick Start Workflow

1. **Primary config file:** `config/skill_atlas/atlas.json` (UTF-8 JSON, loaded by the server and synced to all clients).
2. **Optional localization files:** `config/skill_atlas/lang/<lang>.json` (e.g. `en_us.json`, `zh_cn.json`).
   - **Tip for AI generation:** You may put plain readable text directly in `name`, `tooltip`, `aliases`, and `description` inside `atlas.json` (supporting `&` color codes like `&6Fireball` and `\n` line breaks). Skill Atlas renders plain text directly without requiring separate lang files. Only use translation keys if the user specifically asks for multi-language support.
3. **Apply changes live:** Run `/skillatlas reload` in-game (requires OP permission level 2).
   - If validation fails, the previous file is **never overwritten**, editing is locked until fixed, and the exact error reason is printed in `logs/latest.log`.
4. **In-game visual editor:** Players with edit permission can click the Edit icon inside the Skill Atlas screen (default key `K`) to visually arrange nodes, draw links, and use the **Formula Generator** (added in v1.4.0) to batch-generate levels.

---

## 2. Critical Rules (Read Before Generating!)

Violating any of these rules will cause `/skillatlas reload` to reject the file:

1. **ID format (`pool.id`, `category.id`, `node.id`, `choice_group`, `image.id`):**
   - Regex: `^[a-z0-9_.-]{1,64}$`
   - **NO colons (`:`), NO uppercase letters, NO spaces.** Use `"fire_mastery"`, NOT `"mypack:fire_mastery"` or `"Fire Mastery"`.
2. **Resource IDs (items, attributes, effects, blocks, entities, textures):**
   - Regex: `^[a-z0-9_.-]+:[a-z0-9_.-/]+$` (max 256 chars; omitting namespace defaults to `minecraft:`).
   - Every referenced attribute, mob effect, and item must exist in the game's registries.
3. **Multi-level rewards on the SAME node REPLACE, not stack:**
   - For `attribute`, `effect`, and `immunity`, only the player's **current level** applies.
   - Example: If Level 1 gives `+2` attack damage and Level 2 should total `+4`, Level 2 MUST specify `amount: 4` (not `2`).
   - Rewards from **different** nodes DO stack normally.
   - `command` rewards run once when reaching that level; `cost` and `items` are paid per level-up.
4. **Skill Atlas built-in percentage attributes use `operation: "add"` with whole percentage points:**
   - Example: To give `+15%` mining speed via `skill_atlas:mining_speed`, use `"amount": 15, "operation": "add"` (NOT `0.15` and NOT `multiply_base`).
   - By contrast, vanilla attributes like `minecraft:generic.movement_speed` use `"operation": "multiply_base"` with decimal ratios (`0.1` = `+10%`).
5. **No circular prerequisites (DAG required):**
   - A node cannot depend on itself, list duplicate prerequisites, or form a dependency cycle.
   - Prerequisite `level` is **strictly greater than** (`currentLevel > level`):
     - `level: 0` requires the parent to be at least **Level 1**.
     - `level: 2` requires the parent to be at least **Level 3**.
     - Valid range: `0` to `parent.levels.length - 1` (if `level >= parent.maxLevel`, validation fails!).
6. **Pool-less categories and XP costs:**
   - A category with `"pool": ""` has no point pool. Every level in that category with `cost > 0` MUST set `"experience": true` (spends raw XP points, NOT XP levels).
7. **Hard limits:**
   - Top-level `"format": 1` (required, must be `1`).
   - Max `64` pools, `64` categories, `10000` nodes, `100` levels per node (min `1` level!), `64` prerequisites per node, `32` rewards per level, `16` items per level, `64` targets per pool, `64` canvas images per category.
8. **Coordinate layout guidelines (avoid overlapping nodes!):**
   - Canvas center is `(0, 0)`. `+x` goes right, `+y` goes down.
   - Grid snap is `8` pixels. Standard node visual size (`size: 1.0`) is `26x26` px plus a text label below it.
   - **Recommended spacing:** Place adjacent node centers at least **56–72 px apart horizontally** and **56–64 px apart vertically** (use multiples of `8`, e.g., `64`). For larger keystone nodes (`size: 1.3`–`1.6`), add extra margin (`80–96` px).

---

## 3. Minecraft Version Differences (IMPORTANT!)

Ask the user which Minecraft version they use, or check their target version before writing attribute IDs and tags:

| Concept | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge |
| --- | --- | --- | --- |
| Max Health | `minecraft:generic.max_health` | `minecraft:generic.max_health` | `minecraft:max_health` |
| Attack Damage | `minecraft:generic.attack_damage` | `minecraft:generic.attack_damage` | `minecraft:attack_damage` |
| Attack Speed | `minecraft:generic.attack_speed` | `minecraft:generic.attack_speed` | `minecraft:attack_speed` |
| Armor | `minecraft:generic.armor` | `minecraft:generic.armor` | `minecraft:armor` |
| Armor Toughness | `minecraft:generic.armor_toughness` | `minecraft:generic.armor_toughness` | `minecraft:armor_toughness` |
| Movement Speed | `minecraft:generic.movement_speed` | `minecraft:generic.movement_speed` | `minecraft:movement_speed` |
| Luck | `minecraft:generic.luck` | `minecraft:generic.luck` | `minecraft:luck` |
| Step Height | `forge:step_height_addition` | `minecraft:generic.step_height` | `minecraft:step_height` |
| Swim Speed | `forge:swim_speed` | `neoforge:swim_speed` | `neoforge:swim_speed` |
| Common Tag Prefix | `#forge:...` (e.g. `#forge:ores`) | `#c:...` (e.g. `#c:ores`) | `#c:...` (e.g. `#c:ores`) |
| `items[].nbt` format | Legacy SNBT (prefer omitting) | Serialized ItemStack SNBT (prefer omitting) | Serialized ItemStack SNBT (prefer omitting) |

> **All `skill_atlas:*` attribute IDs are identical across 1.20.1, 1.21.1, and 26.1.2.**

---

## 4. Complete `atlas.json` Schema Reference

### 4.1 Top-Level Object

```json
{
  "format": 1,
  "pools": [ /* PointPool[] */ ],
  "categories": [ /* Category[] */ ],
  "nodes": [ /* SkillNode[] */ ]
}
```

### 4.2 `PointPool` (`pools[]`)

Each pool stores skill points and has one automatic earning method.

| Field | Type | Required / Default | Constraints & Notes |
| --- | --- | --- | --- |
| `id` | string | **Required** | `[a-z0-9_.-]{1,64}`, globally unique |
| `name` | string | `""` | Display name, max 128 chars, supports `&` color codes |
| `color` | string | `"#ECC873"` | Hex color `"#RRGGBB"` |
| `initial` | int | `0` | Starting points granted once per player (`0..1000000000`) |
| `method` | string | `"kill"` | One of: `"kill"`, `"mine"`, `"harvest"`, `"craft"`, `"smelt"`, `"fish"`, `"movement"`, `"advancement"`, `"playtime"` |
| `targets` | string[] | `[]` | Max 64 entries. Empty `[]` matches anything. Supports `#namespace:tag` for `kill`/`mine`/`harvest`/`craft`/`smelt`/`fish`. **Tags are NOT allowed for `advancement`.** Ignored for `movement`/`playtime`. |
| `hostile` | boolean | `true` | Only used when `method == "kill"`. If `true`, only hostile mobs (`Enemy` interface) count. |
| `count` | int | `10` | Threshold required per award (`1..1000000000`). Units: blocks for `movement`, seconds for `playtime`, count for all others. |
| `points` | int | `1` | Points awarded each time `count` is reached (`1..1000000`) |
| `limit` | int | `0` | Max total points earnable via `method` (`0` = unlimited, max `1000000000`) |
| `description` | string | `""` | Progress hint shown in UI, max 1000 chars, supports `&` codes & `\n` |

### 4.3 `Category` (`categories[]`)

| Field | Type | Required / Default | Constraints & Notes |
| --- | --- | --- | --- |
| `id` | string | **Required** | `[a-z0-9_.-]{1,64}`, unique |
| `name` | string | `""` | Display name, max 128 chars |
| `icon` | string \| object | `minecraft:book` | Item ID string `"minecraft:iron_sword"` OR `{"type":"item","id":"minecraft:iron_sword"}` OR `{"type":"texture","id":"namespace:textures/gui/icon.png"}` |
| `pool` | string | `""` | Existing `PointPool.id`, or `""` for no pool (XP-only category) |
| `background` | string | `""` | Optional texture path (`.png` or animated `.gif` from resource packs), `""` for default star-chart canvas |
| `tile` | boolean | `false` | `false`: cover & center-crop; `true`: repeat/tile with canvas pan & zoom |
| `images` | CanvasImage[] | `[]` | Optional decorative canvas images (max 64): `{"id":"bg_1","texture":"namespace:textures/gui/banner.png","x":0,"y":-120,"width":128,"height":64}` (`width`/`height` `1..4096`, supports `.png` and animated `.gif`) |

### 4.4 `SkillNode` (`nodes[]`)

| Field | Type | Required / Default | Constraints & Notes |
| --- | --- | --- | --- |
| `id` | string | **Required** | `[a-z0-9_.-]{1,64}`, unique |
| `category` | string | **Required** | Must match an existing `Category.id` |
| `name` | string | `""` | Display name, max 128 chars |
| `tooltip` | string | `""` | Description text, max 4000 chars, supports `&` color codes and `\n` |
| `aliases` | string | `""` | Optional space-separated extra search keywords, max 512 chars |
| `icon` | string \| object | `minecraft:book` | Item ID string or `{"type":"item"\|"texture","id":"...","nbt":""}` |
| `x` | int | `0` | Canvas X coordinate (`-1000000..1000000`) |
| `y` | int | `0` | Canvas Y coordinate (`-1000000..1000000`) |
| `shape` | string | `"rsquare"` | One of: `"rsquare"`, `"square"`, `"circle"`, `"diamond"`, `"pentagon"`, `"hexagon"`, `"octagon"`, `"heart"`, `"gear"`, `"none"` |
| `size` | number | `1.0` | Visual scale multiplier (`0.5..4.0`) |
| `hidden` | boolean | `false` | If `true`, invisible to players (and search) until prerequisites are met |
| `requirement` | string | `"all"` | `"all"` (all prerequisites required) or `"any"` (at least one prerequisite required) |
| `choice_group` | string | `""` | Optional `[a-z0-9_.-]{1,64}`. Nodes sharing the same `choice_group` are mutually exclusive (learning one locks the rest until reset) |
| `prerequisites` | array | `[]` | Max 64. Each item is either a string `"parent_id"` or `{"node":"parent_id","line":true,"level":0}`. `line` controls whether an arrow is rendered; `level` means `parentLevel > level` (`0` = at least Lv.1). |
| `levels` | SkillLevel[] | **Required** | **1 to 100 entries (cannot be empty).** Index 0 is Level 1, index 1 is Level 2, etc. |

### 4.5 `SkillLevel` (`nodes[].levels[]`)

| Field | Type | Required / Default | Constraints & Notes |
| --- | --- | --- | --- |
| `cost` | int | `1` | Points or raw XP spent to unlock this level (`0..1000000000`) |
| `experience` | boolean | inherits category | `true` = spend raw XP points; `false` = spend category's skill pool points. Omit to default to `true` if category has no pool, else `false`. |
| `rewards` | Reward[] | `[]` | Max 32 rewards active at this level (see 4.6) |
| `items` | ItemCost[] | `[]` | Max 16 consumed delivery items: `{"item":"minecraft:diamond","count":4}` (`count` `1..99999`, optional `"nbt":""`). Consumed from inventory + offhand on learn, never refunded. |

### 4.6 `Reward` (`levels[].rewards[]`)

Four valid `type` values:

1. **Attribute (`"attribute"`):**
   ```json
   {
     "type": "attribute",
     "target": "minecraft:generic.attack_damage",
     "amount": 2.0,
     "operation": "add"
   }
   ```
   - `amount`: `-1000000` to `1000000`.
   - `operation`:
     - `"add"`: flat addition (`+amount`)
     - `"multiply_base"`: percentage of base (`0.1` = `+10%`)
     - `"multiply_total"`: final multiplier (`0.1` = `×1.1`)

2. **Periodic Status Effect (`"effect"`):**
   ```json
   {
     "type": "effect",
     "target": "minecraft:night_vision",
     "amplifier": 0,
     "duration": 400,
     "interval": 100,
     "particles": false,
     "icon": true
   }
   ```
   - `amplifier`: `0..255` (**`0` = Level I**, `1` = Level II, etc.)
   - `duration`: `1..1000000` ticks (`20` ticks = 1 second; for Night Vision use `>= 300` to avoid flicker)
   - `interval`: `1..72000` ticks (how often the effect is re-applied)

3. **Status Effect Immunity (`"immunity"`):**
   ```json
   {
     "type": "immunity",
     "target": "minecraft:poison"
   }
   ```
   - Removes and blocks the specified mob effect while active.

4. **One-time Command (`"command"`):**
   ```json
   {
     "type": "command",
     "command": "give @s minecraft:golden_apple 1"
   }
   ```
   - Runs once as the player (`@s`) at permission level 2 when reaching this level. Leading `/` is automatically stripped. Max 1024 chars, cannot be empty.

---

## 5. Built-in Skill Atlas Attributes (`skill_atlas:*`)

All 24 custom player attributes below are built into Skill Atlas on 1.20.1, 1.21.1, and 26.1.2. **Always use `"operation": "add"`** with these attributes.

| Attribute ID | Range | Unit / Meaning |
| --- | --- | --- |
| `skill_atlas:healing_efficiency` | `-100 .. 10000` | `%` bonus to all healing received (`20` = `+20%`) |
| `skill_atlas:experience_gain` | `-100 .. 10000` | `%` bonus XP from mob kills (`25` = `+25%`) |
| `skill_atlas:looting` | `0 .. 100` | Extra Looting enchantment levels (decimals roll probabilistically, e.g. `1.5` = +1 guaranteed + 50% chance of +2) |
| `skill_atlas:fortune` | `0 .. 100` | Extra Fortune enchantment levels (decimals roll probabilistically; respects Silk Touch) |
| `skill_atlas:mining_speed` | `-100 .. 10000` | `%` faster block breaking (`10` = `+10%`) |
| `skill_atlas:stone_mineral_chance` | `0 .. 100` | `%` chance when mining `minecraft:stone` to drop bonus coal/iron/gold/diamond |
| `skill_atlas:mining_experience` | `-100 .. 10000` | `%` bonus XP from ore blocks (`20` = `+20%`) |
| `skill_atlas:harvest_experience` | `0 .. 10000` | Flat raw XP points granted per mature crop harvest (`1` = +1 XP) |
| `skill_atlas:crop_replanting` | `0 .. 1` | `> 0` enables automatic replanting of harvested crops using seeds |
| `skill_atlas:harvest_yield` | `0 .. 10000` | `%` extra crop harvest yield (`30` = `+30%`) |
| `skill_atlas:fishing_lure` | `0 .. 100` | Extra Lure levels (stacks with rod enchantment) |
| `skill_atlas:fishing_luck` | `0 .. 100` | Extra Luck of the Sea levels |
| `skill_atlas:jump_height` | `0 .. 16` | Extra jump height in blocks (`0.2` = `+0.2` blocks) |
| `skill_atlas:fall_damage_reduction` | `0 .. 100` | `%` fall damage reduced (`40` = `-40%` fall damage) |
| `skill_atlas:elytra_speed` | `0 .. 10000` | `%` extra elytra gliding speed (`50` = `+50%`) |
| `skill_atlas:smelting_speed` | `0 .. 10000` | `%` faster furnace/blast furnace/smoker processing (`10` = `+10%`) |
| `skill_atlas:raw_ore_double_chance` | `0 .. 100` | `%` chance to double raw ore smelting output (`30` = `30%`) |
| `skill_atlas:fuel_efficiency` | `0 .. 10000` | `%` longer furnace fuel burn duration (`50` = `+50%`) |
| `skill_atlas:smelting_experience` | `0 .. 10000` | `%` bonus XP when taking smelted items (`100` = `+100%`) |
| `skill_atlas:enchanting_cap` | `0 .. 100` | `%` chance for enchanting table enchantments to roll +1 extra level (can exceed vanilla max) |
| `skill_atlas:lapis_saving` | `0 .. 100` | `%` chance not to consume lapis lazuli when enchanting |
| `skill_atlas:enchanting_refund` | `0 .. 100` | `%` of spent enchanting XP refunded |
| `skill_atlas:book_copy` | `0 .. 100` | `%` chance to receive a duplicate enchanted book when enchanting a book |
| `skill_atlas:enchanting_insight` | `0 .. 1` | `> 0` previews all enchantments in all 3 enchanting table slots |

---

## 6. Complete Valid Example (`atlas.json`)

This minimal working example demonstrates pools, pool-less XP categories, multi-level replacement rewards, prerequisites with `level` thresholds, hidden nodes, mutually exclusive `choice_group` branches, delivery items, effects, immunities, and commands (formatted for 1.20.1 / 1.21.1; for 26.1.2 change `minecraft:generic.attack_damage` to `minecraft:attack_damage`):

```json
{
  "format": 1,
  "pools": [
    {
      "id": "warrior_pts",
      "name": "Warrior Points",
      "color": "#E0685A",
      "initial": 2,
      "method": "kill",
      "targets": [],
      "hostile": true,
      "count": 8,
      "points": 1,
      "limit": 0,
      "description": "Defeat 8 hostile mobs to earn 1 Warrior Point."
    }
  ],
  "categories": [
    {
      "id": "warrior",
      "name": "Warrior",
      "icon": { "type": "item", "id": "minecraft:iron_sword" },
      "pool": "warrior_pts",
      "background": "",
      "tile": false,
      "images": []
    },
    {
      "id": "arcane_xp",
      "name": "Arcane",
      "icon": { "type": "item", "id": "minecraft:experience_bottle" },
      "pool": "",
      "background": "",
      "tile": false,
      "images": []
    }
  ],
  "nodes": [
    {
      "id": "blade_training",
      "category": "warrior",
      "name": "Blade Training",
      "tooltip": "Increases melee attack damage.",
      "icon": { "type": "item", "id": "minecraft:iron_sword" },
      "x": 0,
      "y": 0,
      "shape": "rsquare",
      "size": 1.0,
      "hidden": false,
      "requirement": "all",
      "prerequisites": [],
      "levels": [
        {
          "cost": 1,
          "experience": false,
          "rewards": [
            { "type": "attribute", "target": "minecraft:generic.attack_damage", "amount": 1.0, "operation": "add" }
          ]
        },
        {
          "cost": 2,
          "experience": false,
          "rewards": [
            { "type": "attribute", "target": "minecraft:generic.attack_damage", "amount": 2.5, "operation": "add" }
          ],
          "items": [
            { "item": "minecraft:iron_ingot", "count": 8 }
          ]
        },
        {
          "cost": 3,
          "experience": false,
          "rewards": [
            { "type": "attribute", "target": "minecraft:generic.attack_damage", "amount": 4.0, "operation": "add" }
          ],
          "items": [
            { "item": "minecraft:diamond", "count": 2 }
          ]
        }
      ]
    },
    {
      "id": "path_berserker",
      "category": "warrior",
      "name": "&cBerserker Path",
      "tooltip": "Choose raw strength. Locks Guardian Path.",
      "icon": { "type": "item", "id": "minecraft:netherite_axe" },
      "x": -72,
      "y": 72,
      "shape": "diamond",
      "size": 1.25,
      "hidden": false,
      "requirement": "all",
      "choice_group": "warrior_subclass",
      "prerequisites": [
        { "node": "blade_training", "line": true, "level": 1 }
      ],
      "levels": [
        {
          "cost": 3,
          "experience": false,
          "rewards": [
            { "type": "effect", "target": "minecraft:strength", "amplifier": 0, "duration": 100, "interval": 40, "particles": false, "icon": true },
            { "type": "command", "command": "title @s actionbar {\"text\":\"Chosen: Berserker\"}" }
          ]
        }
      ]
    },
    {
      "id": "path_guardian",
      "category": "warrior",
      "name": "&bGuardian Path",
      "tooltip": "Choose durability and poison immunity. Locks Berserker Path.",
      "icon": { "type": "item", "id": "minecraft:shield" },
      "x": 72,
      "y": 72,
      "shape": "hexagon",
      "size": 1.25,
      "hidden": false,
      "requirement": "all",
      "choice_group": "warrior_subclass",
      "prerequisites": [
        { "node": "blade_training", "line": true, "level": 1 }
      ],
      "levels": [
        {
          "cost": 3,
          "experience": false,
          "rewards": [
            { "type": "attribute", "target": "skill_atlas:healing_efficiency", "amount": 25.0, "operation": "add" },
            { "type": "immunity", "target": "minecraft:poison" }
          ]
        }
      ]
    },
    {
      "id": "xp_insight",
      "category": "arcane_xp",
      "name": "Arcane Insight",
      "tooltip": "Spends raw XP points directly.",
      "icon": { "type": "item", "id": "minecraft:enchanting_table" },
      "x": 0,
      "y": 0,
      "shape": "circle",
      "size": 1.2,
      "hidden": false,
      "requirement": "all",
      "prerequisites": [],
      "levels": [
        {
          "cost": 100,
          "experience": true,
          "rewards": [
            { "type": "attribute", "target": "skill_atlas:enchanting_insight", "amount": 1.0, "operation": "add" }
          ]
        }
      ]
    }
  ]
}
```

---

## 7. In-Game Formula Generator (v1.4.0+)

If building multi-level skills inside the in-game editor, open a node and click **Formula Generator** next to **Add level**:
- **Variables:** `lv` (or `level`, `x` = `1..max`), `max`, `pi`, `e`
- **Operators:** `+`, `-`, `*`, `/`, `%`, `^` (right-associative power), `()`, `>`, `>=`, `<`, `<=`, `==`, `!=`
- **Functions:** `sqrt(x)`, `cbrt(x)`, `pow(a, b)`, `exp(x)`, `ln(x)`, `log(x)` (base 10), `log(x, base)`, `log10(x)`, `log2(x)`, `floor(x)`, `ceil(x)`, `round(x)`, `round(x, digits)`, `abs(x)`, `min(...)`, `max(...)`, `clamp(x, lo, hi)`, `if(cond, thenVal, elseVal)` (short-circuiting)
- **Special behaviors:**
  - Delivery item counts and effect levels evaluating to `<= 0` are automatically skipped on that level (e.g. `if(lv >= 4, lv - 3, 0)`).
  - Commands support `${formula}` interpolation (e.g. `give @s minecraft:diamond ${lv}`), avoiding conflicts with NBT `{}`.

---

## 8. Optional: KubeJS Integration Quick Reference

Only applicable when **KubeJS** is installed alongside Skill Atlas (put scripts in `kubejs/server_scripts/`):

- **Modify or build trees dynamically (`SkillAtlasEvents.definitions`):**
  ```js
  SkillAtlasEvents.definitions(event => {
    event.createPool('quests')
      .name('Quest Points')
      .color(0x5FD6C8)
      .initial(1)
      .acquisition('advancement', 1, 1, 0)

    event.createCategory('adventure')
      .name('Adventure')
      .icon('minecraft:compass')
      .pool('quests')

    event.createNode('swift_explorer')
      .category('adventure')
      .name('Swift Explorer')
      .description('Increases movement speed.')
      .icon('minecraft:leather_boots')
      .position(0, 0)
      .level(1, rank => {
        rank.cost({ type: 'skill_points', amount: 1 })
        rank.item('minecraft:sugar', 8)
        rank.attributeReward('minecraft:generic.movement_speed', 0.1, 'multiply_base')
      })
      .level(2, rank => {
        rank.cost({ type: 'experience', amount: 150 })
        rank.attributeReward('minecraft:generic.movement_speed', 0.2, 'multiply_base')
      })
  })
  ```
- **Other server events on `SkillAtlasEvents`:** `beforeLearn`, `learned`, `learnFailed`, `beforeReset`, `reset`, `pointsChanging`, `pointsChanged`, `progressing`, `levelChanged`, `learningCost`, `conditionTypes`, `rewardTypes`, `acquisitionTypes`, `reloaded`, `reloadFailed`.
- **Player server API (`SkillAtlas.player(serverPlayer)`):** `getLevel(id)`, `hasSkill(id, minLevel)`, `isMaxed(id)`, `getLearnedSkills()`, `getPoints(poolId)`, `addPoints(poolId, amount, reason)`, `spendPoints(...)`, `setPoints(...)`, `addProgress(...)`, `checkLearn(id)`, `canLearn(id)`, `learn(id)`, `grant(id, level)`, `reset({ scope: 'all' | 'category' | 'node', id })`, `open(categoryId)`, `focus(nodeId)`.

---

## 9. Admin Commands & Config Files

- **Commands (OP level 2):**
  - `/skillatlas reload` — reload `config/skill_atlas/atlas.json` and `lang/*.json`
  - `/skillatlas points get|add|set <player> <pool> [amount]`
  - `/skillatlas grant <player> <node> [level]`
  - `/skillatlas reset <player> [node]`
- **Server config:** `<world>/serverconfig/skill_atlas-server.toml` (`editPermissionLevel`, `countCreativePlayers`, `notifyPointGain`, `allowSkillRefund`, `refundExperienceCost`)
- **Client config:** `config/skill_atlas-client.toml` (`virtualHeight`, `panelWidth`, `panelHeight`, `showNodeNames`)

---

## 10. Prompt Template for Players Using AI

You can paste this prompt together with this wiki to any AI:

> Using the Skill Atlas Wiki above, generate a complete, valid `config/skill_atlas/atlas.json` for Minecraft **[1.20.1 Forge / 1.21.1 NeoForge / 26.1.2 NeoForge]**.
> Theme: **[describe your RPG classes / categories / progression here]**.
> Requirements:
> - Follow all ID regex (`[a-z0-9_.-]{1,64}`), DAG, coordinate spacing (`64px` grid), and version-specific attribute ID rules strictly.
> - Remember multi-level rewards on the same node replace previous levels, and `skill_atlas:*` percentage attributes use `operation: "add"`.
> - Output ONLY valid JSON that passes `/skillatlas reload` without errors.
