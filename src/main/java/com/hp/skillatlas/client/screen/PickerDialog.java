package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.SkillAtlas;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        public String nbt = "";
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

    public record Mode(Component label, List<Entry> entries) {
    }

    private record Scored(Entry entry, int score) {
    }

    private static final Map<String, Component> MOD_NAMES = new HashMap<>();
    private static final Map<String, Integer> LAST_MODE = new HashMap<>();
    private final List<Mode> modes;
    private final String memory;
    private final Consumer<Entry> onPick;
    private final Set<ResourceLocation> thumbs = new HashSet<>();
    private final Map<ResourceLocation, int[]> thumbSizes = new HashMap<>();
    private List<Entry> all;
    private int mode;
    private int rowHeight = 18;
    private float cell;
    private boolean showIds;
    private TextInput search;
    private ListView<Entry> list;
    private String query = "";

    public PickerDialog(Component title, List<Entry> entries, Consumer<String> onPick) {
        this(title, List.of(new Mode(Component.empty(), entries)), "", e -> onPick.accept(e.id));
    }

    public PickerDialog(Component title, List<Mode> modes, String memory, Consumer<Entry> onPick) {
        super(title, 300, 300);
        this.modes = modes;
        this.memory = memory;
        this.onPick = onPick;
        this.mode = Math.max(0, Math.min(modes.size() - 1, LAST_MODE.getOrDefault(memory, 0)));
        this.all = modes.get(mode).entries();
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
        return itemStacks(e -> onPick.accept(e.id));
    }

    public static PickerDialog itemStacks(Consumer<Entry> onPick) {
        List<Entry> all = new ArrayList<>();
        for (Map.Entry<ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
            Item item = e.getValue();
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = e.getKey().location();
            all.add(new Entry(id.toString(), item.getDescription(), stackIcon(new ItemStack(item)), modName(id)));
        }
        List<Entry> owned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id == null) {
                    continue;
                }
                String nbt = "";
                if (stack.getTag() != null) {
                    nbt = stack.getTag().toString();
                }
                if (!seen.add(id + nbt)) {
                    continue;
                }
                Entry e = new Entry(id.toString(), stack.getHoverName(), stackIcon(stack.copyWithCount(1)), modName(id));
                e.nbt = nbt;
                owned.add(e);
            }
        }
        List<Mode> modes = List.of(new Mode(Component.translatable("skill_atlas.ui.pick.all_items"), all), new Mode(Component.translatable("skill_atlas.ui.pick.inventory_items"), owned));
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.item"), modes, "items", onPick).grid(20);
    }

    public static PickerDialog textures(Consumer<String> onPick) {
        List<ResourceLocation> ids = new ArrayList<>(Minecraft.getInstance().getResourceManager().listResources("textures", rl -> rl.getPath().endsWith(".png")).keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        List<Entry> out = new ArrayList<>(ids.size());
        PickerDialog d = new PickerDialog(Component.translatable("skill_atlas.ui.pick.texture"), List.of(new Mode(Component.empty(), out)), "textures", e -> onPick.accept(e.id));
        d.grid(38);
        for (ResourceLocation rl : ids) {
            String path = rl.getPath().substring("textures/".length());
            out.add(new Entry(rl.toString(), Component.literal(rl.getNamespace() + ":" + path), (x, y, size) -> d.thumb(rl, x, y, size), modName(rl)));
        }
        SkillAtlas.LOGGER.debug("Texture picker listed {} png resources", out.size());
        return d;
    }

    private PickerDialog grid(float size) {
        cell = size;
        rowHeight = (int) size;
        return this;
    }

    private int[] pngSize(ResourceLocation rl) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(rl)) {
            byte[] head = in.readNBytes(24);
            if (head.length == 24) {
                ByteBuffer b = ByteBuffer.wrap(head);
                int w = b.getInt(16);
                int h = b.getInt(20);
                if (w > 0 && h > 0) {
                    return new int[]{w, h};
                }
            }
        } catch (IOException e) {
            SkillAtlas.LOGGER.debug("Failed to read texture header {}: {}", rl, e.toString());
        }
        return new int[]{1, 1};
    }

    private void thumb(ResourceLocation rl, float x, float y, float size) {
        if (Minecraft.getInstance().getTextureManager().getTexture(rl, null) == null) {
            thumbs.add(rl);
        }
        int[] sz = thumbSizes.computeIfAbsent(rl, this::pngSize);
        float w = sz[0];
        float h = sz[1];
        float v1 = 1.0F;
        if (h > w && h % w == 0) {
            v1 = w / h;
            h = w;
        }
        float scale = size / Math.max(w, h);
        float dw = w * scale;
        float dh = h * scale;
        Gfx.texture(rl, x + (size - dw) / 2.0F, y + (size - dh) / 2.0F, dw, dh, 0.0F, 0.0F, 1.0F, v1, 0xFFFFFFFF);
    }

    @Override
    public void onClosed() {
        super.onClosed();
        for (ResourceLocation rl : thumbs) {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
        thumbs.clear();
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
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.block"), out, onPick).grid(20);
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
        return new PickerDialog(Component.translatable("skill_atlas.ui.pick.effect"), out, onPick).grid(22);
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
                out.add(new Entry(id, Names.tag(id), spriteIcon(Sprites.FOLDER), modName(tag.location())));
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
        PickerDialog picker = new PickerDialog(Component.translatable("skill_atlas.ui.pick.node"), out, onPick);
        picker.showIds = true;
        picker.prefW = 380;
        return picker;
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
        onPick.accept(e);
    }

    @Override
    protected void build() {
        float top = contentY();
        if (modes.size() > 1) {
            float bw = contentW() / modes.size();
            for (int i = 0; i < modes.size(); i++) {
                int index = i;
                Button tab = add(new Button(modes.get(i).label(), () -> {
                    mode = index;
                    LAST_MODE.put(memory, index);
                    all = modes.get(index).entries();
                    refresh();
                }));
                tab.active(() -> mode == index);
                tab.bounds(contentX() + bw * i, top, bw - 2, 16);
            }
            top += 20;
        }
        search = add(new TextInput(query));
        search.max(64).placeholder(Component.translatable("skill_atlas.ui.search"));
        search.bounds(contentX(), top, contentW(), 16);
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
        list.rowHeight = rowHeight;
        list.cellWidth = cell;
        list.bounds(contentX(), top + 20, contentW(), contentY() + contentH() - top - 20);
        list.empty = Component.translatable("skill_atlas.ui.no_results");
        list.onClick = (e, button) -> {
            if (button == 0) {
                pick(e);
            }
        };
        list.tooltips = e -> {
            List<Component> lines = new ArrayList<>();
            lines.add(e.name);
            if (showIds) {
                lines.add(Component.translatable("skill_atlas.ui.pick.node_id", e.id).withStyle(s -> s.withColor(0x8A93A8)));
            }
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
        if (cell > 0) {
            float s = Math.min(rw, rh) - 4;
            e.icon.draw(rx + (rw - s) / 2.0F, ry + (rh - s) / 2.0F, s);
            return;
        }
        float size = rh - 2;
        e.icon.draw(rx + 2, ry + 1, size);
        int color = Theme.TEXT;
        if (hovered) {
            color = 0xFFFFFFFF;
        }
        float nameWidth = rw - size - 10;
        float textY = ry + (rh - 8) / 2.0F;
        if (showIds) {
            Component id = Gfx.ellipsize(Component.literal(e.id), nameWidth * 0.5F);
            float idWidth = Gfx.width(id);
            Gfx.text(id, rx + rw - idWidth - 4, textY, Theme.MUTED);
            nameWidth -= idWidth + 8;
        }
        Gfx.text(Gfx.ellipsize(e.name, nameWidth).getVisualOrderText(), rx + size + 6, textY, color, true);
    }
}
