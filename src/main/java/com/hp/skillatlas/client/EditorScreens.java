package com.hp.skillatlas.client;

import com.google.gson.*;
import com.hp.skillatlas.core.*;
import com.hp.skillatlas.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Consumer;

public final class EditorScreens {
    private EditorScreens() {}
    private static void open(Screen screen) { Minecraft.getInstance().setScreen(screen); }
    private static <T> T copy(T object, Class<T> type) { return Definition.JSON.fromJson(Definition.JSON.toJson(object), type); }
    private static String label(String key) { return FormScreen.tr(key).getString(); }
    public static String rewardDescription(Definition.Reward r) {
        return switch (r.type) {
            case "attribute" -> label("reward_attribute") + " " + registeredName("attribute", r.target) + " " + r.amount + " (" + label("value." + r.operation) + ")";
            case "effect" -> label("reward_effect") + " " + registeredName("effect", r.target) + " " + (r.amplifier + 1) + " / " + r.interval + " tick";
            default -> label("reward_immunity") + " " + String.join(", ", r.effects.stream().map(id -> registeredName("effect", id)).toList());
        };
    }
    private static String registeredName(String type, String id) {
        if (id.startsWith("#")) return label(id.equals("#forge:ores") ? "tag_ores" : "tag_target");
        var key = ResourceLocation.tryParse(id); if (key == null) return label("unknown_target");
        return switch (type) {
            case "item" -> { var item = ForgeRegistries.ITEMS.getValue(key); yield item == null ? label("unknown_target") : Component.translatable(item.getDescriptionId()).getString(); }
            case "effect" -> { var effect = ForgeRegistries.MOB_EFFECTS.getValue(key); yield effect == null ? label("unknown_target") : effect.getDisplayName().getString(); }
            case "attribute" -> { var attribute = ForgeRegistries.ATTRIBUTES.getValue(key); yield attribute == null ? label("unknown_target") : Component.translatable(attribute.getDescriptionId()).getString(); }
            case "kill" -> { var entity = ForgeRegistries.ENTITY_TYPES.getValue(key); yield entity == null ? label("unknown_target") : entity.getDescription().getString(); }
            case "mine" -> { var block = ForgeRegistries.BLOCKS.getValue(key); yield block == null ? label("unknown_target") : block.getName().getString(); }
            default -> label("unknown_target");
        };
    }
    private static String nodeName(Definition d, String id) { var node = d.nodes.get(id); return node == null ? label("unknown_target") : DisplayNames.text(node.name); }
    private static List<SearchListScreen.Entry> registry(String type) {
        return ClientState.registries.getOrDefault(type, List.of()).stream().map(id -> new SearchListScreen.Entry(id, registeredName(type, id))).toList();
    }
    private static List<SearchListScreen.Entry> nodes(Definition d) { return d.nodes.entrySet().stream().map(e -> new SearchListScreen.Entry(e.getKey(), DisplayNames.text(e.getValue().name) + " · " + DisplayNames.text(d.categories.get(e.getValue().category).name))).toList(); }
    private static List<SearchListScreen.Entry> categories(Definition d) { return d.categories.entrySet().stream().map(e -> new SearchListScreen.Entry(e.getKey(), DisplayNames.text(e.getValue().name))).toList(); }
    private static List<SearchListScreen.Entry> pools(Definition d) { return d.pools.entrySet().stream().map(e -> new SearchListScreen.Entry(e.getKey(), DisplayNames.text(e.getValue().name))).toList(); }
    private static List<SearchListScreen.Entry> costPools(Definition d) {
        List<SearchListScreen.Entry> result = new ArrayList<>(); result.add(new SearchListScreen.Entry("", label("inherit_pool"))); result.addAll(pools(d)); return result;
    }
    public static String costDescription(Definition d, Definition.Node n, int index) {
        String pool = AtlasLogic.pointPool(d, n, index); var definition = d.pools.get(pool);
        return n.levels.get(index).cost + " " + (definition == null ? label("unknown_target") : DisplayNames.text(definition.name));
    }
    public static String itemDescription(Definition.ItemCost item) {
        String name = registeredName("item", item.item);
        return name + " × " + item.count + (item.nbt.isBlank() ? "" : " " + item.nbt);
    }
    public static void detail(AtlasScreen atlas, String id) {
        open(new FormScreen(atlas, "details") {
            @Override protected void build() {
                var n = ClientState.definition.nodes.get(id); if (n == null) return;
                int current = ClientState.progress.level(id);
                info("name", DisplayNames.text(n.name)); info("level", current + " / " + n.levels.size());
                for (String line : DisplayNames.text(n.tooltip).split("\\n")) info("tooltip", line);
                if (current > 0) for (var r : n.levels.get(current - 1).rewards) info("current_rewards", rewardDescription(r));
                if (current < n.levels.size()) {
                    var next = n.levels.get(current); info("cost", costDescription(ClientState.definition, n, current));
                    String pool = AtlasLogic.pointPool(ClientState.definition, n, current);
                    info("available_points", Long.toString(ClientState.progress.pools.getOrDefault(pool, new PlayerProgress.Balance()).points));
                    for (var item : next.items) info("item_costs", itemDescription(item));
                    if (!next.items.isEmpty()) info("delivery_status", label(minecraft.player != null && ItemDelivery.available(minecraft.player.getInventory(), next.items) ? "items_ready" : "items_missing"));
                    for (var r : next.rewards) info("next_rewards", rewardDescription(r));
                    action("learn", () -> {
                        JsonObject request = new JsonObject(); request.addProperty("node", id); request.addProperty("level", current); request.addProperty("revision", ClientState.definition.revision);
                        ClientState.request("learn", request); open(atlas);
                    });
                }
                info("prerequisite_mode", label("value." + n.prerequisiteMode));
                for (var p : n.prerequisites) { var parent = ClientState.definition.nodes.get(p.node); if (parent != null) info("prerequisites", (AtlasLogic.visible(parent, ClientState.progress, false) ? DisplayNames.text(parent.name) : label("hidden_skill")) + " ≥ " + p.level + (ClientState.progress.level(p.node) >= p.level ? " ✓" : " ×")); }
            }
            @Override protected void save() {}
        });
    }
    public static void node(AtlasScreen atlas, Set<String> selected) {
        List<String> ids = selected.stream().filter(atlas.data().nodes::containsKey).toList(); if (ids.isEmpty()) return;
        Definition.Node first = atlas.data().nodes.get(ids.get(0)); boolean batch = ids.size() > 1;
        open(new FormScreen(atlas, "node_editor") {
            private void field(String key, Runnable build) {
                if (batch) { choice("apply_" + key, "false", "false", "true");
                    JsonElement base = Definition.JSON.toJsonTree(first).getAsJsonObject().get(key);
                    if (base != null && ids.stream().anyMatch(id -> !base.equals(Definition.JSON.toJsonTree(atlas.data().nodes.get(id)).getAsJsonObject().get(key)))) info("mixed", label("multiple_values"));
                    if (key.equals("max_levels") && ids.stream().anyMatch(id -> atlas.data().nodes.get(id).levels.size() != first.levels.size())) info("mixed", label("multiple_values"));
                }
                build.run();
            }
            private boolean applies(String key) { return !batch || bool("apply_" + key); }
            @Override protected void build() {
                info("selection", Integer.toString(ids.size()));
                field("name", () -> text("name", DisplayNames.text(first.name))); field("tooltip", () -> text("tooltip", DisplayNames.text(first.tooltip), true)); field("aliases", () -> text("aliases", first.aliases));
                field("category", () -> pick("category", first.category, categories(atlas.data())));
                field("iconType", () -> choice("iconType", first.iconType, "item", "texture"));
                field("icon", () -> pick("icon", first.icon, registry("item"))); field("iconNbt", () -> text("iconNbt", first.iconNbt, true));
                field("x", () -> text("x", Double.toString(first.x))); field("y", () -> text("y", Double.toString(first.y)));
                field("max_levels", () -> text("max_levels", Integer.toString(first.levels.size())));
                field("hideUntilReady", () -> choice("hideUntilReady", Boolean.toString(first.hideUntilReady), "false", "true"));
                field("prerequisiteMode", () -> choice("prerequisiteMode", first.prerequisiteMode, "all", "any"));
                action("levels", () -> levels(atlas, this, ids)); action("prerequisites", () -> prerequisites(atlas, this, ids));
                action("delete", () -> {
                    open(new UiConfirmScreen(ok -> { if (ok) { ids.forEach(atlas.data().nodes::remove); atlas.data().nodes.values().forEach(n -> n.prerequisites.removeIf(r -> ids.contains(r.node))); atlas.data().lines.removeIf(l -> ids.contains(l.from) || ids.contains(l.to)); atlas.touch(); open(atlas); } else open(this); }, tr("delete"), tr("delete_confirm")));
                });
            }
            @Override protected void save() {
                List<Definition.Node> changed = new ArrayList<>();
                for (String id : ids) {
                    Definition.Node n = atlas.data().nodes.get(id).copy();
                    if (applies("name")) n.name = DisplayNames.edited(n.name, value("name")); if (applies("tooltip")) n.tooltip = DisplayNames.edited(n.tooltip, value("tooltip")); if (applies("aliases")) n.aliases = value("aliases");
                    if (applies("category")) { if (!atlas.data().categories.containsKey(value("category"))) throw new IllegalArgumentException(label("error.category")); n.category = value("category"); }
                    if (applies("iconType")) n.iconType = value("iconType"); if (applies("icon")) n.icon = value("icon"); if (applies("iconNbt")) n.iconNbt = value("iconNbt");
                    if (applies("x")) n.x = decimal("x"); if (applies("y")) n.y = decimal("y");
                    if (applies("hideUntilReady")) n.hideUntilReady = bool("hideUntilReady"); if (applies("prerequisiteMode")) n.prerequisiteMode = value("prerequisiteMode");
                    if (applies("max_levels")) { int max = integer("max_levels"); if (max < 1 || max > 100) throw new IllegalArgumentException(label("error.level_range")); while (n.levels.size() < max) n.levels.add(new Definition.Level()); while (n.levels.size() > max) n.levels.remove(n.levels.size() - 1); }
                    changed.add(n);
                }
                for (int i = 0; i < ids.size(); i++) atlas.data().nodes.put(ids.get(i), changed.get(i));
                atlas.data().lines.removeIf(l -> !atlas.data().nodes.get(l.from).category.equals(atlas.data().nodes.get(l.to).category)); atlas.touch();
            }
        });
    }
    private static void levels(AtlasScreen atlas, Screen parent, List<String> ids) {
        open(new FormScreen(parent, "levels") {
            @Override protected void build() { text("level_index", "1"); action("edit_level", () -> {
                int level;
                try { level = integer("level_index"); } catch (Exception ex) { fail(tr("error.missing_level")); return; }
                if (level < 1 || ids.stream().anyMatch(id -> atlas.data().nodes.get(id).levels.size() < level)) { fail(tr("error.missing_level")); return; }
                level(atlas, this, ids, level - 1);
            }); }
            @Override protected void save() {}
        });
    }
    private static void level(AtlasScreen atlas, Screen parent, List<String> ids, int index) {
        open(new FormScreen(parent, "edit_level") {
            @Override protected void build() {
                info("level", Integer.toString(index + 1)); if (ids.size() > 1) choice("apply_cost", "false", "false", "true");
                text("cost", Long.toString(atlas.data().nodes.get(ids.get(0)).levels.get(index).cost));
                if (ids.stream().anyMatch(id -> atlas.data().nodes.get(id).levels.get(index).cost != atlas.data().nodes.get(ids.get(0)).levels.get(index).cost)) info("mixed", label("multiple_values"));
                if (ids.size() > 1) choice("apply_cost_pool", "false", "false", "true");
                String pool = atlas.data().nodes.get(ids.get(0)).levels.get(index).pool; pick("cost_pool", pool, costPools(atlas.data()));
                info("pool_hint", label("pool_hint_text"));
                if (ids.stream().anyMatch(id -> !atlas.data().nodes.get(id).levels.get(index).pool.equals(pool))) info("mixed", label("multiple_values"));
                action("item_costs", () -> itemCosts(atlas, this, ids, index));
                action("rewards", () -> rewards(atlas, this, ids, index));
            }
            @Override protected void save() {
                boolean setCost = ids.size() == 1 || bool("apply_cost"), setPool = ids.size() == 1 || bool("apply_cost_pool");
                long cost = setCost ? number("cost") : 0; String pool = value("cost_pool").trim();
                if (setCost && (cost < 0 || cost > AtlasLogic.LIMIT)) throw new IllegalArgumentException(label("error.cost"));
                if (setPool && !pool.isEmpty() && !atlas.data().pools.containsKey(pool)) throw new IllegalArgumentException(label("error.pool"));
                for (String id : ids) { var level = atlas.data().nodes.get(id).levels.get(index); if (setCost) level.cost = cost; if (setPool) level.pool = pool; }
                if (setCost || setPool) atlas.touch();
            }
        });
    }
    private static void itemCosts(AtlasScreen atlas, Screen parent, List<String> ids, int index) {
        List<Definition.ItemCost> items = new ArrayList<>(); atlas.data().nodes.get(ids.get(0)).levels.get(index).items.forEach(r -> items.add(copy(r, Definition.ItemCost.class)));
        open(new FormScreen(parent, "item_costs") {
            @Override protected void build() {
                info("delivery_help", label("delivery_help_text"));
                if (ids.size() > 1) {
                    choice("apply_items", "false", "false", "true"); info("batch_replace", label("batch_replace_items"));
                    if (ids.stream().anyMatch(id -> !Definition.JSON.toJson(items).equals(Definition.JSON.toJson(atlas.data().nodes.get(id).levels.get(index).items)))) info("mixed", label("multiple_values"));
                }
                for (int i = 0; i < items.size(); i++) { int at = i; actionText((i + 1) + ". " + itemDescription(items.get(at)), () -> itemCost(this, items.get(at), r -> { items.set(at, r); open(this); }, () -> { items.remove(at); open(this); })); }
                action("new_item_cost", () -> itemCost(this, new Definition.ItemCost(), r -> { items.add(r); open(this); }, () -> open(this)));
            }
            @Override protected void save() {
                if (ids.size() > 1 && !bool("apply_items")) return;
                if (items.size() > 128) throw new IllegalArgumentException(label("error.item_cost"));
                for (String id : ids) { var target = atlas.data().nodes.get(id).levels.get(index).items; target.clear(); items.forEach(r -> target.add(copy(r, Definition.ItemCost.class))); } atlas.touch();
            }
        });
    }
    private static void itemCost(Screen parent, Definition.ItemCost source, Consumer<Definition.ItemCost> saved, Runnable deleted) {
        open(new FormScreen(parent, "item_cost_editor") {
            @Override protected void build() {
                pick("delivery_item", source.item, registry("item")); text("delivery_count", Integer.toString(source.count)); text("delivery_nbt", source.nbt, true);
                info("nbt_hint", label("delivery_nbt_hint")); action("delete", deleted);
            }
            @Override protected void save() {
                var item = new Definition.ItemCost(); item.item = value("delivery_item").trim(); item.count = integer("delivery_count"); item.nbt = value("delivery_nbt").trim();
                if (ItemDelivery.validate(item) != null) throw new IllegalArgumentException(label("error.item_cost")); saved.accept(item);
            }
        });
    }
    private static void rewards(AtlasScreen atlas, Screen parent, List<String> ids, int index) {
        List<Definition.Reward> rewards = new ArrayList<>(); atlas.data().nodes.get(ids.get(0)).levels.get(index).rewards.forEach(r -> rewards.add(copy(r, Definition.Reward.class)));
        open(new FormScreen(parent, "rewards") {
            @Override protected void build() {
                if (ids.size() > 1) info("batch_replace", label("batch_replace_rewards"));
                for (int i = 0; i < rewards.size(); i++) { int at = i; actionText((i + 1) + ". " + rewardDescription(rewards.get(i)), () -> reward(this, rewards.get(at), r -> { rewards.set(at, r); open(this); }, () -> { rewards.remove(at); open(this); })); }
                action("new_reward", () -> reward(this, new Definition.Reward(), r -> { rewards.add(r); open(this); }, () -> open(this)));
            }
            @Override protected void save() { for (String id : ids) { var target = atlas.data().nodes.get(id).levels.get(index).rewards; target.clear(); rewards.forEach(r -> target.add(copy(r, Definition.Reward.class))); } atlas.touch(); }
        });
    }
    private static void reward(Screen parent, Definition.Reward source, Consumer<Definition.Reward> saved, Runnable deleted) {
        open(new FormScreen(parent, "reward_editor") {
            @Override protected void build() {
                choice("type", source.type, "attribute", "effect", "immunity");
                info("reward_help", label("reward_help_text"));
                text("target", source.target); action("choose_attribute", () -> open(new SearchListScreen(this, "choose_attribute", registry("attribute"), id -> { values.put("target", id); open(this); })));
                action("choose_effect", () -> open(new SearchListScreen(this, "choose_effect", registry("effect"), id -> { values.put("target", id); open(this); })));
                text("amount", Double.toString(source.amount)); choice("operation", source.operation, "addition", "multiply_base", "multiply_total");
                text("amplifier", Integer.toString(source.amplifier)); text("duration", Integer.toString(source.duration)); text("interval", Integer.toString(source.interval));
                choice("particles", Boolean.toString(source.particles), "false", "true"); choice("showIcon", Boolean.toString(source.showIcon), "false", "true");
                text("effects", String.join("\n", source.effects), true);
                action("add_immunity_effect", () -> open(new SearchListScreen(this, "choose_effect", registry("effect"), id -> { values.put("effects", value("effects") + (value("effects").isBlank() ? "" : "\n") + id); open(this); })));
                action("delete", deleted);
            }
            @Override protected void save() {
                var r = copy(source, Definition.Reward.class); r.type = value("type"); r.target = value("target"); r.amount = decimal("amount"); r.operation = value("operation");
                r.amplifier = integer("amplifier"); r.duration = integer("duration"); r.interval = integer("interval"); r.particles = bool("particles"); r.showIcon = bool("showIcon");
                r.effects = new ArrayList<>(Arrays.stream(value("effects").split("[\\s,]+" )).filter(s -> !s.isBlank()).distinct().toList());
                if (r.amplifier < 0 || r.amplifier > 255 || r.duration < 1 || r.duration > 72000 || r.interval < 1 || r.interval > 72000) throw new IllegalArgumentException(label("error.reward"));
                saved.accept(r);
            }
        });
    }
    private static void prerequisites(AtlasScreen atlas, Screen parent, List<String> ids) {
        List<Definition.Requirement> requirements = new ArrayList<>(); atlas.data().nodes.get(ids.get(0)).prerequisites.forEach(r -> requirements.add(copy(r, Definition.Requirement.class)));
        open(new FormScreen(parent, "prerequisites") {
            @Override protected void build() {
                if (ids.size() > 1) info("batch_replace", label("batch_replace_prerequisites"));
                choice("draw_lines", "false", "false", "true");
                for (int i = 0; i < requirements.size(); i++) { int at = i; var r = requirements.get(i); actionText(nodeName(atlas.data(), r.node) + " ≥ " + r.level, () -> requirement(this, r, value -> { requirements.set(at, value); open(this); }, () -> { requirements.remove(at); open(this); }, atlas.data())); }
                action("new_prerequisite", () -> requirement(this, new Definition.Requirement(), r -> { requirements.add(r); open(this); }, () -> open(this), atlas.data()));
            }
            @Override protected void save() {
                for (String id : ids) {
                    var n = atlas.data().nodes.get(id); n.prerequisites.clear(); requirements.forEach(r -> n.prerequisites.add(copy(r, Definition.Requirement.class)));
                    if (bool("draw_lines")) for (var r : requirements) { var from = atlas.data().nodes.get(r.node); if (from != null && from.category.equals(n.category) && atlas.data().lines.stream().noneMatch(l -> l.from.equals(r.node) && l.to.equals(id))) atlas.data().lines.add(new Definition.Line(r.node, id)); }
                }
                atlas.touch();
            }
        });
    }
    private static void requirement(Screen parent, Definition.Requirement source, Consumer<Definition.Requirement> saved, Runnable deleted, Definition d) {
        open(new FormScreen(parent, "prerequisite_editor") {
            @Override protected void build() { pick("node", source.node, nodes(d)); text("level", Integer.toString(source.level)); action("delete", deleted); }
            @Override protected void save() { int level = integer("level"); var node = d.nodes.get(value("node")); if (node == null || level < 1 || level > node.levels.size()) throw new IllegalArgumentException(label("error.prerequisite")); saved.accept(new Definition.Requirement(value("node"), level)); }
        });
    }
    public static void lines(AtlasScreen atlas) {
        open(new FormScreen(atlas, "links") {
            @Override protected void build() {
                for (int i = 0; i < atlas.data().lines.size(); i++) { int at = i; var line = atlas.data().lines.get(i); actionText(nodeName(atlas.data(), line.from) + " → " + nodeName(atlas.data(), line.to), () -> line(atlas, this, line, at)); }
                action("new_line", () -> line(atlas, this, new Definition.Line(), -1));
            }
            @Override protected void save() {}
        });
    }
    private static void line(AtlasScreen atlas, Screen parent, Definition.Line source, int index) {
        open(new FormScreen(parent, "line_editor") {
            @Override protected void build() { pick("from", source.from, nodes(atlas.data())); pick("to", source.to, nodes(atlas.data())); choice("visible", Boolean.toString(source.visible), "true", "false"); if (index >= 0) action("delete", () -> { atlas.data().lines.remove(index); atlas.touch(); open(parent); }); }
            @Override protected void save() {
                var from = atlas.data().nodes.get(value("from")); var to = atlas.data().nodes.get(value("to"));
                if (from == null || to == null || from == to || !from.category.equals(to.category)) throw new IllegalArgumentException(label("error.line"));
                var line = new Definition.Line(value("from"), value("to")); line.visible = bool("visible");
                if (index >= 0) atlas.data().lines.set(index, line); else atlas.data().lines.add(line); atlas.touch();
            }
        });
    }
    public static void manage(AtlasScreen atlas) {
        open(new FormScreen(atlas, "manage") {
            @Override protected void build() {
                action("categories", () -> categoryList(atlas, this)); action("pools", () -> poolList(atlas, this)); action("rules", () -> ruleList(atlas, this));
            }
            @Override protected void save() {}
        });
    }
    private static void categoryList(AtlasScreen atlas, Screen parent) {
        open(new FormScreen(parent, "categories") {
            @Override protected void build() { atlas.data().categories.forEach((id, c) -> actionText(DisplayNames.text(c.name), () -> category(atlas, this, id))); action("new_category", () -> category(atlas, this, null)); }
            @Override protected void save() {}
        });
    }
    private static void category(AtlasScreen atlas, Screen parent, String id) {
        var source = id == null ? new Definition.Category() : atlas.data().categories.get(id);
        open(new FormScreen(parent, "category_editor") {
            @Override protected void build() { if (id == null) text("id", "category_" + (atlas.data().categories.size() + 1)); else info("id", id); text("name", DisplayNames.text(source.name)); pick("pool", source.pool, pools(atlas.data())); text("order", Integer.toString(source.order));
                if (id != null) action("delete", () -> { if (atlas.data().nodes.values().stream().anyMatch(n -> n.category.equals(id))) { atlas.status(tr("error.category_in_use"), "error"); return; } atlas.data().categories.remove(id); atlas.touch(); open(parent); });
            }
            @Override protected void save() {
                String key = id == null ? value("id") : id;
                if (!AtlasLogic.id(key) || (id == null && atlas.data().categories.containsKey(key)) || !atlas.data().pools.containsKey(value("pool"))) throw new IllegalArgumentException(label("error.category"));
                var c = new Definition.Category(); c.name = DisplayNames.edited(source.name, value("name")); c.pool = value("pool"); c.order = integer("order"); atlas.data().categories.put(key, c); atlas.touch();
            }
        });
    }
    private static void poolList(AtlasScreen atlas, Screen parent) {
        open(new FormScreen(parent, "pools") {
            @Override protected void build() { atlas.data().pools.forEach((id, p) -> actionText(DisplayNames.text(p.name), () -> pool(atlas, this, id))); action("new_pool", () -> pool(atlas, this, null)); }
            @Override protected void save() {}
        });
    }
    private static void pool(AtlasScreen atlas, Screen parent, String id) {
        var source = id == null ? new Definition.Pool() : atlas.data().pools.get(id);
        open(new FormScreen(parent, "pool_editor") {
            @Override protected void build() { if (id == null) text("id", "pool_" + (atlas.data().pools.size() + 1)); else info("id", id); text("name", DisplayNames.text(source.name)); text("baseExperience", Long.toString(source.baseExperience)); text("experienceGrowth", Long.toString(source.experienceGrowth)); text("pointsPerLevel", Long.toString(source.pointsPerLevel));
                if (id != null) action("delete", () -> { if (atlas.data().categories.values().stream().anyMatch(c -> c.pool.equals(id)) || atlas.data().rules.values().stream().anyMatch(r -> r.pool.equals(id)) || atlas.data().nodes.values().stream().anyMatch(n -> n.levels.stream().anyMatch(l -> l.pool.equals(id)))) { atlas.status(tr("error.pool_in_use"), "error"); return; } atlas.data().pools.remove(id); atlas.touch(); open(parent); });
            }
            @Override protected void save() {
                String key = id == null ? value("id") : id;
                if (!AtlasLogic.id(key) || (id == null && atlas.data().pools.containsKey(key))) throw new IllegalArgumentException(label("error.pool"));
                var p = new Definition.Pool(); p.name = DisplayNames.edited(source.name, value("name")); p.baseExperience = number("baseExperience"); p.experienceGrowth = number("experienceGrowth"); p.pointsPerLevel = number("pointsPerLevel");
                if (p.baseExperience < 1 || p.baseExperience > 1_000_000_000L || p.experienceGrowth < 0 || p.experienceGrowth > 1_000_000 || p.pointsPerLevel < 0 || p.pointsPerLevel > 1_000_000) throw new IllegalArgumentException(label("error.pool"));
                atlas.data().pools.put(key, p); atlas.touch();
            }
        });
    }
    private static void ruleList(AtlasScreen atlas, Screen parent) {
        open(new FormScreen(parent, "rules") {
            @Override protected void build() { atlas.data().rules.forEach((id, r) -> actionText(label("value." + r.event) + " · " + registeredName(r.event, r.target) + " · " + DisplayNames.text(atlas.data().pools.get(r.pool).name), () -> rule(atlas, this, id))); action("new_rule", () -> rule(atlas, this, null)); }
            @Override protected void save() {}
        });
    }
    private static void rule(AtlasScreen atlas, Screen parent, String id) {
        var source = id == null ? new Definition.Rule() : atlas.data().rules.get(id);
        open(new FormScreen(parent, "rule_editor") {
            @Override protected void build() {
                if (id == null) text("id", "rule_" + (atlas.data().rules.size() + 1)); else info("id", id);
                choice("event", source.event, "kill", "mine"); text("target", source.target);
                action("choose_kill", () -> open(new SearchListScreen(this, "choose_kill", registry("kill"), key -> { values.put("target", key); open(this); })));
                action("choose_mine", () -> open(new SearchListScreen(this, "choose_mine", registry("mine"), key -> { values.put("target", key); open(this); })));
                info("target_hint", label("target_hint_text")); pick("pool", source.pool, pools(atlas.data())); choice("mode", source.mode, "count", "experience"); text("threshold", Long.toString(source.threshold)); text("amount", Long.toString(source.amount));
                if (id != null) action("delete", () -> { atlas.data().rules.remove(id); atlas.touch(); open(parent); });
            }
            @Override protected void save() {
                String key = id == null ? value("id") : id;
                if (!AtlasLogic.id(key) || (id == null && atlas.data().rules.containsKey(key))) throw new IllegalArgumentException(label("error.rule"));
                var r = new Definition.Rule(); r.event = value("event"); r.target = value("target"); r.pool = value("pool"); r.mode = value("mode"); r.threshold = number("threshold"); r.amount = number("amount");
                if (!atlas.data().pools.containsKey(r.pool) || r.threshold < 1 || r.threshold > AtlasLogic.LIMIT || r.amount < 1 || r.amount > 1_000_000_000L) throw new IllegalArgumentException(label("error.rule"));
                atlas.data().rules.put(key, r); atlas.touch();
            }
        });
    }
    public static void files(AtlasScreen atlas) {
        open(new FormScreen(atlas, "files") {
            private void send(String op, boolean definition) { JsonObject o = new JsonObject(); o.addProperty("name", value("filename")); if (definition) o.add("definition", Definition.JSON.toJsonTree(atlas.data())); ClientState.request(op, o); }
            @Override protected void build() {
                info("server_files", label("server_files_text")); text("filename", "atlas_export");
                action("import", () -> send("import", false)); action("export", () -> send("export", true)); action("save_draft", () -> send("save_draft", true)); action("load_draft", () -> ClientState.request("load_draft"));
                if (atlas.data().revision != ClientState.definition.revision) {
                    info("draft_conflict", label("draft_conflict_text"));
                    action("rebase_draft", () -> open(new UiConfirmScreen(ok -> { if (ok) atlas.rebaseDraft(); open(this); }, tr("rebase_draft"), tr("rebase_confirm"))));
                }
            }
            @Override protected void save() {}
        });
    }
}
