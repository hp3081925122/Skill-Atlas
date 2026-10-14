package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.PinyinText;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.ListView;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.SkillNode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PickerDialog extends Dialog {
    public interface IconDrawer {
        void draw(float x, float y, float size);
    }

    public static final class Entry {
        public final String id;
        public final Component name;
        public final IconDrawer icon;
        public final Component detail;
        private String[] forms;

        public Entry(String id, Component name, IconDrawer icon, Component detail) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.detail = detail;
        }

        private String[] forms() {
            if (forms == null) {
                String[] f = PinyinText.forms(name.getString());
                String[] out = new String[f.length + 1];
                System.arraycopy(f, 0, out, 0, f.length);
                out[f.length] = id.toLowerCase(Locale.ROOT);
                forms = out;
            }
            return forms;
        }
    }

    private record Scored(Entry entry, int score) {
    }

    private static final Map<String, Component> MOD_NAMES = new HashMap<>();
    private final List<Entry> all;
    private final Consumer<String> onPick;
    private TextInput search;
    private ListView<Entry> list;
    private String query = "";

    public PickerDialog(Component title, List<Entry> entries, Consumer<String> onPick) {
        super(title, 300, 300);
        this.all = entries;
        this.onPick = onPick;
    }

    private static Component modName(ResourceLocation id) {
        return MOD_NAMES.computeIfAbsent(id.getNamespace(), ns -> {
            String name = ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
            return Component.literal(name).withStyle(s -> s.withColor(0x7FA8FF));
        });
    }

    private static IconDrawer stackIcon(ItemStack stack) {
        return (x, y, size) -> Gfx.item(stack, x, y, size);
    }

    private static IconDrawer spriteIcon(int index) {
        return (x, y, size) -> Gfx.icon(index, x + (size - 12) / 2.0F, y + (size - 12) / 2.0F, 0xFFB8BED0);
    }

    public static PickerDialog items(Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
            Item item = e.getValue();
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = e.getKey().location();
            out.add(new Entry(id.toString(), item.getDescription(), stackIcon(new ItemStack(item)), modName(id)));
        }
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.item"), out, onPick);
    }

    public static PickerDialog blocks(Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<Block>, Block> e : ForgeRegistries.BLOCKS.getEntries()) {
            Block block = e.getValue();
            ResourceLocation id = e.getKey().location();
            if (id.getPath().equals("air")) {
                continue;
            }
            IconDrawer icon = spriteIcon(Sprites.FOLDER);
            if (block.asItem() != Items.AIR) {
                icon = stackIcon(new ItemStack(block.asItem()));
            }
            out.add(new Entry(id.toString(), block.getName(), icon, modName(id)));
        }
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.block"), out, onPick);
    }

    public static PickerDialog entities(Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>> e : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            EntityType<?> type = e.getValue();
            ResourceLocation id = e.getKey().location();
            IconDrawer icon = spriteIcon(Sprites.TARGET);
            SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(type);
            if (egg != null) {
                icon = stackIcon(new ItemStack(egg));
            }
            out.add(new Entry(id.toString(), type.getDescription(), icon, modName(id)));
        }
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.entity"), out, onPick);
    }

    public static PickerDialog attributes(Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<Attribute>, Attribute> e : ForgeRegistries.ATTRIBUTES.getEntries()) {
            ResourceLocation id = e.getKey().location();
            out.add(new Entry(id.toString(), Component.translatable(e.getValue().getDescriptionId()), spriteIcon(Sprites.STAR), modName(id)));
        }
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.attribute"), out, onPick);
    }

    public static PickerDialog effects(Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<MobEffect>, MobEffect> e : ForgeRegistries.MOB_EFFECTS.getEntries()) {
            MobEffect effect = e.getValue();
            ResourceLocation id = e.getKey().location();
            IconDrawer icon = (x, y, size) -> {
                TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
                Gfx.flush();
                PoseStack pose = Gfx.pose();
                pose.pushPose();
                pose.translate(x, y, 0);
                pose.scale(size / 18.0F, size / 18.0F, 1.0F);
                Gfx.graphics().blit(0, 0, 0, 18, 18, sprite);
                pose.popPose();
            };
            out.add(new Entry(id.toString(), effect.getDisplayName(), icon, modName(id)));
        }
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.effect"), out, onPick);
    }

    public static PickerDialog advancements(Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            for (Advancement a : conn.getAdvancements().getAdvancements().getAllAdvancements()) {
                if (a.getDisplay() == null) {
                    continue;
                }
                out.add(new Entry(a.getId().toString(), a.getDisplay().getTitle(), stackIcon(a.getDisplay().getIcon()), modName(a.getId())));
            }
        }
        out.sort(Comparator.comparing(e -> e.id));
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.advancement"), out, onPick);
    }

    public static <T> PickerDialog tags(ResourceKey<? extends Registry<T>> registry, Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            Optional<? extends Registry<T>> reg = conn.registryAccess().registry(registry);
            reg.ifPresent(r -> r.getTagNames().forEach(tag -> {
                String id = "#" + tag.location();
                out.add(new Entry(id, Component.literal(id), spriteIcon(Sprites.FOLDER), modName(tag.location())));
            }));
        }
        out.sort(Comparator.comparing(e -> e.id));
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.tag"), out, onPick);
    }

    public static PickerDialog nodes(Predicate<SkillNode> filter, Consumer<String> onPick) {
        List<Entry> out = new ArrayList<>();
        List<String> order = new ArrayList<>(ClientAtlas.definition.categories.keySet());
        List<SkillNode> sorted = new ArrayList<>(ClientAtlas.definition.nodes.values());
        sorted.sort(Comparator.comparingInt((SkillNode n) -> order.indexOf(n.category)).thenComparing(Names::nodePlain));
        for (SkillNode n : sorted) {
            if (!filter.test(n)) {
                continue;
            }
            Category c = ClientAtlas.definition.categories.get(n.category);
            Component detail = Component.empty();
            if (c != null) {
                detail = Names.category(c).withStyle(s -> s.withColor(0x8A93A8));
            }
            SkillNode node = n;
            out.add(new Entry(n.id, Names.node(n), (x, y, size) -> Icons.draw(node.icon, x, y, size), detail));
        }
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.node"), out, onPick);
    }

    private List<Entry> filtered() {
        String q = PinyinText.normalize(query);
        if (q.isEmpty()) {
            return all;
        }
        List<Scored> scored = new ArrayList<>();
        for (Entry e : all) {
            int best = 0;
            String[] forms = e.forms();
            for (int i = 0; i < forms.length; i++) {
                String f = forms[i];
                if (f.equals(q)) {
                    best = Math.max(best, 3);
                } else if (f.startsWith(q)) {
                    best = Math.max(best, 2);
                } else if (f.contains(q)) {
                    best = Math.max(best, 1);
                }
            }
            if (best > 0) {
                scored.add(new Scored(e, best));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> -s.score));
        List<Entry> out = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            out.add(s.entry);
        }
        return out;
    }

    private void refresh() {
        List<Entry> items = filtered();
        list.setItems(items);
        list.resetScroll();
        setStatus(Component.translatable("skill_atlas.ui.pick.count", items.size()), Theme.MUTED);
    }

    private void pick(Entry e) {
        close();
        onPick.accept(e.id);
    }

    @Override
    protected void build() {
        search = add(new TextInput(query));
        search.max(64).placeholder(Component.translatable("skill_atlas.ui.search"));
        search.bounds(contentX(), contentY(), contentW(), 16);
        search.onChange = s -> {
            query = s;
            refresh();
        };
        search.onEnter = () -> {
            if (!list.items().isEmpty()) {
                pick(list.items().get(0));
            }
        };
        list = add(new ListView<>(this::renderRow));
        list.rowHeight = 18;
        list.bounds(contentX(), contentY() + 20, contentW(), contentH() - 20);
        list.empty = Component.translatable("skill_atlas.ui.no_results");
        list.onClick = (e, button) -> {
            if (button == 0) {
                pick(e);
            }
        };
        list.tooltips = e -> {
            List<Component> lines = new ArrayList<>();
            lines.add(e.name);
            if (e.detail != null && !e.detail.getString().isEmpty()) {
                lines.add(e.detail);
            }
            return lines;
        };
        footer(new Button(Component.translatable("skill_atlas.ui.cancel"), this::close));
        refresh();
        Ui.focus(search);
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.key(key, scan, mods);
    }

    private void renderRow(Entry e, float rx, float ry, float rw, float rh, boolean hovered) {
        e.icon.draw(rx + 2, ry + 1, 16);
        int color = Theme.TEXT;
        if (hovered) {
            color = 0xFFFFFFFF;
        }
        Gfx.text(Gfx.ellipsize(e.name, rw - 26).getVisualOrderText(), rx + 22, ry + 5, color, true);
    }
}
