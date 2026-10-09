package com.hp.skillatlas.client;

import com.google.gson.JsonObject;
import com.hp.skillatlas.core.*;
import com.hp.skillatlas.model.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import com.mojang.math.Axis;
import java.util.*;

public final class AtlasScreen extends Screen {
    private static final Map<String, View> VIEWS = new HashMap<>();
    private static final class View { double x, y, scale = 1; }
    private final NodeClipboard clipboard = new NodeClipboard();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, String> searchIndex = new LinkedHashMap<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final Map<String, Boolean> textures = new HashMap<>();
    private final Map<String, Boolean> itemAvailability = new HashMap<>();
    private int inventoryTick = -1;
    private Definition draft;
    private boolean editing, dirty, publishing;
    private Boolean pendingClose;
    private String category = "", query = "";
    private EditBox search;
    private int left, top, categoryScroll;
    private int searchOffset;
    private double mouseX, mouseY, startX, startY;
    private boolean panning, moving, boxing;
    private Component status = Component.empty();
    private String language = "";
    public AtlasScreen() { super(FormScreen.tr("title")); }
    public static void clearViews() { VIEWS.clear(); }
    public Definition data() { return editing ? draft : ClientState.definition; }
    public boolean isEditing() { return editing; }
    public Set<String> selection() { return Set.copyOf(selected); }
    public void touch() { dirty = true; invalidateResources(); }
    public void rebaseDraft() { if (editing) { draft.revision = ClientState.definition.revision; touch(); } }
    public void refresh() { init(minecraft, width, height); }
    public void invalidateResources() { searchIndex.clear(); icons.clear(); textures.clear(); itemAvailability.clear(); }
    public void receivedDefinition() {
        if (editing && !dirty) { draft = ClientState.definition.copy(); }
        if (editing && !ClientState.canEdit) { editing = false; draft = null; dirty = false; }
        invalidateResources();
    }
    public void receiveDraft(Definition d) { draft = d; editing = true; dirty = true; invalidateResources(); if (minecraft != null) minecraft.setScreen(this); }
    public void status(Component message, String code) {
        status = message;
        if (code.equals("published")) { publishing = false; dirty = false; draft = ClientState.definition.copy(); invalidateResources(); }
        if (code.equals("saved") && pendingClose != null) { boolean exit = pendingClose; pendingClose = null; dirty = false; editing = false; minecraft.setScreen(exit ? null : this); }
        if (code.equals("error")) { publishing = false; pendingClose = null; }
    }
    private View view() { return VIEWS.computeIfAbsent(category, k -> new View()); }
    @Override protected void init() {
        left = Math.min(130, Math.max(80, width / 4)); top = editing ? 80 : 55;
        if (!data().categories.containsKey(category)) category = categories().stream().findFirst().orElse("");
        search = new UiEditBox(font, left + 5, 6, Math.max(70, width - left - 90), 20, FormScreen.tr("search"));
        search.setHint(FormScreen.tr("search_hint")); search.setMaxLength(256); search.setValue(query); search.setResponder(v -> { query = v; searchOffset = 0; }); addRenderableWidget(search);
        if (ClientState.canEdit) addRenderableWidget(Button.builder(FormScreen.tr(editing ? "view_mode" : "edit_mode"), b -> {
            if (editing && dirty) { closePrompt(false); return; }
            editing = !editing; if (editing) draft = ClientState.definition.copy(); selected.clear(); invalidateResources(); refresh();
        }).bounds(width - 80, 6, 75, 20).build(UiButton::new));
        addRenderableWidget(Button.builder(FormScreen.tr("reset_view"), b -> VIEWS.put(category, new View())).bounds(5, 30, left - 10, 20).build(UiButton::new));
        if (editing) {
            int w = Math.max(35, (width - left - 30) / 5), x = left + 5;
            addRenderableWidget(Button.builder(FormScreen.tr("edit_selected"), b -> { if (!selected.isEmpty()) EditorScreens.node(this, selected); }).bounds(x, 30, w, 20).build(UiButton::new));
            addRenderableWidget(Button.builder(FormScreen.tr("manage"), b -> EditorScreens.manage(this)).bounds(x + w + 4, 30, w, 20).build(UiButton::new));
            addRenderableWidget(Button.builder(FormScreen.tr("links"), b -> EditorScreens.lines(this)).bounds(x + (w + 4) * 2, 30, w, 20).build(UiButton::new));
            addRenderableWidget(Button.builder(FormScreen.tr("files"), b -> EditorScreens.files(this)).bounds(x + (w + 4) * 3, 30, w, 20).build(UiButton::new));
            addRenderableWidget(Button.builder(FormScreen.tr("publish"), b -> publish()).bounds(x + (w + 4) * 4, 30, w, 20).build(UiButton::new));
        }
    }
    private List<String> categories() { return data().categories.entrySet().stream().sorted(Comparator.comparingInt(e -> e.getValue().order)).map(Map.Entry::getKey).toList(); }
    private boolean canvas(double x, double y) { return x >= left && x < width && y >= top && y < height - 29; }
    private double sx(double x) { return left + (width - left) / 2.0 + view().x + x * view().scale; }
    private double sy(double y) { return top + (height - 29 - top) / 2.0 + view().y + y * view().scale; }
    private double wx(double x) { return (x - left - (width - left) / 2.0 - view().x) / view().scale; }
    private double wy(double y) { return (y - top - (height - 29 - top) / 2.0 - view().y) / view().scale; }
    private String hit(double x, double y) {
        String result = null;
        for (var e : data().nodes.entrySet()) if (e.getValue().category.equals(category) && AtlasLogic.visible(e.getValue(), ClientState.progress, editing)) {
            double size = 15 * view().scale;
            if (Math.abs(x - sx(e.getValue().x)) <= size && Math.abs(y - sy(e.getValue().y)) <= size) result = e.getKey();
        }
        return result;
    }
    private List<String> results() {
        if (query.isBlank()) return List.of();
        if (searchIndex.isEmpty()) data().nodes.forEach((id, n) -> searchIndex.put(id, TextSearch.index(DisplayNames.searchText(n.name) + " " + DisplayNames.searchText(n.tooltip) + " " + n.aliases)));
        return data().nodes.entrySet().stream().filter(e -> AtlasLogic.visible(e.getValue(), ClientState.progress, editing) && TextSearch.matches(searchIndex.get(e.getKey()), query)).map(Map.Entry::getKey).toList();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        mouseX = mx; mouseY = my;
        if (minecraft.player != null && inventoryTick != minecraft.player.tickCount) { inventoryTick = minecraft.player.tickCount; itemAvailability.clear(); }
        String currentLanguage = minecraft.getLanguageManager().getSelected(); if (!currentLanguage.equals(language)) { language = currentLanguage; invalidateResources(); }
        UiTheme.draw(g, "background", 0, 0, width, height); UiTheme.draw(g, "sidebar", 0, top, left, height - 29 - top);
        UiTheme.draw(g, "canvas", left, top, width - left, height - 29 - top); UiTheme.draw(g, "toolbar", 0, 0, width, top); UiTheme.draw(g, "footer", 0, height - 29, width, 29);
        g.drawString(font, title, 7, 11, UiTheme.color("title"));
        List<String> categories = categories(); int slots = Math.max(1, (height - top - 75) / 24); categoryScroll = Math.max(0, Math.min(categoryScroll, Math.max(0, categories.size() - slots)));
        for (int i = 0; i < slots && i + categoryScroll < categories.size(); i++) {
            String id = categories.get(i + categoryScroll); int y = top + 7 + i * 24;
            UiTheme.draw(g, id.equals(category) ? "category_selected" : "category", 4, y - 3, left - 8, 20);
            g.drawString(font, font.plainSubstrByWidth(DisplayNames.text(data().categories.get(id).name), left - 14), 8, y + 2, UiTheme.color(id.equals(category) ? "category_selected" : "category"));
        }
        if (data().categories.containsKey(category)) {
            String poolId = data().categories.get(category).pool; var pool = data().pools.get(poolId); var balance = ClientState.progress.pools.getOrDefault(poolId, new PlayerProgress.Balance());
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("skill_atlas.points", balance.points).getString(), left - 12), 6, height - 68, UiTheme.color("points"));
            g.drawString(font, Component.translatable("skill_atlas.experience_level", balance.level), 6, height - 55, UiTheme.color("experience"));
            if (pool != null) g.drawString(font, font.plainSubstrByWidth(balance.experience + "/" + AtlasLogic.requirement(pool, balance.level), left - 12), 6, height - 42, UiTheme.color("experience"));
        }
        g.enableScissor(left, top, width, height - 29);
        int spacing = Math.max(8, (int)(UiTheme.metric("gridSpacing") * view().scale));
        for (int x = left + Math.floorMod((int)sx(0) - left, spacing); x < width; x += spacing) for (int y = top + Math.floorMod((int)sy(0) - top, spacing); y < height - 29; y += spacing) UiTheme.draw(g, "grid", x, y, UiTheme.metric("gridSize"), UiTheme.metric("gridSize"));
        for (var line : data().lines) {
            var a = data().nodes.get(line.from); var b = data().nodes.get(line.to);
            if (!line.visible || a == null || b == null || !a.category.equals(category) || !b.category.equals(category) || !AtlasLogic.visible(a, ClientState.progress, editing) || !AtlasLogic.visible(b, ClientState.progress, editing)) continue;
            drawLine(g, sx(a.x), sy(a.y), sx(b.x), sy(b.y), ClientState.progress.level(line.from) > 0 && ClientState.progress.level(line.to) > 0);
        }
        for (var e : data().nodes.entrySet()) {
            var n = e.getValue(); if (!n.category.equals(category) || !AtlasLogic.visible(n, ClientState.progress, editing)) continue;
            double x = sx(n.x), y = sy(n.y), size = 15 * view().scale;
            if (x + size < left || x - size > width || y + size < top || y - size > height - 29) continue;
            int level = ClientState.progress.level(e.getKey()); boolean ready = AtlasLogic.ready(n, ClientState.progress);
            boolean affordable = false;
            if (level < n.levels.size()) {
                String pool = AtlasLogic.pointPool(data(), n, level); long points = ClientState.progress.pools.getOrDefault(pool, new PlayerProgress.Balance()).points;
                affordable = points >= n.levels.get(level).cost && itemAvailability.computeIfAbsent(e.getKey() + ":" + level, key -> minecraft.player != null && ItemDelivery.available(minecraft.player.getInventory(), n.levels.get(level).items));
            }
            String texture = selected.contains(e.getKey()) ? "node_selected" : level >= n.levels.size() ? "node_mastered" : level > 0 ? "node_learned" : ready && affordable ? "node_available" : "node_locked";
            g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale((float)view().scale, (float)view().scale, 1);
            UiTheme.draw(g, texture, -16, -16, 32, 32);
            renderIcon(g, e.getKey(), n);
            g.drawCenteredString(font, level + "/" + n.levels.size(), 0, 18, UiTheme.color("text")); g.pose().popPose();
            if (view().scale >= 0.7) g.drawCenteredString(font, font.plainSubstrByWidth(DisplayNames.text(n.name), 140), (int)x, (int)(y - size - 13), UiTheme.color("text"));
        }
        if (boxing) {
            int x1 = (int)Math.min(startX, mx), x2 = (int)Math.max(startX, mx), y1 = (int)Math.min(startY, my), y2 = (int)Math.max(startY, my);
            UiTheme.draw(g, "selection", x1, y1, x2 - x1, y2 - y1);
        }
        List<String> results = results();
        int resultSlots = Math.max(1, (height - top - 29) / 23); searchOffset = Math.max(0, Math.min(searchOffset, Math.max(0, results.size() - resultSlots)));
        for (int i = 0; i < resultSlots && i + searchOffset < results.size(); i++) { int y = top + i * 23; UiTheme.draw(g, mx >= left + 5 && mx < width - 5 && my >= y && my < y + 22 ? "result_hover" : "result", left + 5, y, width - left - 10, 22); var n = data().nodes.get(results.get(i + searchOffset)); g.drawString(font, font.plainSubstrByWidth(DisplayNames.text(n.name) + " · " + DisplayNames.text(data().categories.get(n.category).name), width - left - 20), left + 10, y + 7, UiTheme.color("text")); }
        g.disableScissor();
        if (editing) g.drawString(font, Component.translatable("skill_atlas.selection", selected.size()), left + 5, 58, UiTheme.color("label"));
        g.drawString(font, font.plainSubstrByWidth(status.getString().isBlank() ? FormScreen.tr(editing ? "editor_help" : "viewer_help").getString() : status.getString(), width - 12), 6, height - 20, UiTheme.color("muted"));
        super.render(g, mx, my, partial);
        if (canvas(mx, my) && results.isEmpty()) { String id = hit(mx, my); if (id != null) tooltip(g, id, mx, my); }
    }
    private void renderIcon(GuiGraphics g, String id, Definition.Node n) {
        if ("texture".equals(n.iconType)) {
            ResourceLocation texture = ResourceLocation.tryParse(n.icon);
            if (texture != null && textures.computeIfAbsent(n.icon, key -> minecraft.getResourceManager().getResource(texture).isPresent())) { g.blit(texture, -10, -10, 0, 0, 20, 20, 20, 20); return; }
        }
        ItemStack stack = icons.computeIfAbsent(id, key -> {
            ResourceLocation itemId = ResourceLocation.tryParse(n.icon); Item item = itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId) ? null : ForgeRegistries.ITEMS.getValue(itemId);
            ItemStack icon = new ItemStack(item == null || "texture".equals(n.iconType) ? Items.BARRIER : item);
            if (!n.iconNbt.isBlank()) try { icon.setTag(TagParser.parseTag(n.iconNbt)); } catch (Exception ignored) {}
            return icon;
        }); g.renderItem(stack, -8, -8);
    }
    private void tooltip(GuiGraphics g, String id, int x, int y) {
        var n = data().nodes.get(id); List<Component> lines = new ArrayList<>(); lines.add(Component.literal(DisplayNames.text(n.name)));
        lines.add(Component.translatable("skill_atlas.node_level", ClientState.progress.level(id), n.levels.size()));
        for (String line : DisplayNames.text(n.tooltip).split("\\n")) lines.add(Component.literal(line));
        int level = ClientState.progress.level(id);
        if (level > 0) for (var reward : n.levels.get(level - 1).rewards) lines.add(Component.literal(EditorScreens.rewardDescription(reward)));
        if (level < n.levels.size()) {
            lines.add(Component.translatable("skill_atlas.cost_value", EditorScreens.costDescription(data(), n, level)));
            for (var item : n.levels.get(level).items) lines.add(Component.translatable("skill_atlas.item_cost_line", EditorScreens.itemDescription(item)));
            for (var reward : n.levels.get(level).rewards) lines.add(Component.translatable("skill_atlas.next_reward_line", EditorScreens.rewardDescription(reward)));
        }
        for (var r : n.prerequisites) { var parent = data().nodes.get(r.node); if (parent != null) lines.add(Component.translatable("skill_atlas.prerequisite_entry", AtlasLogic.visible(parent, ClientState.progress, editing) ? DisplayNames.text(parent.name) : FormScreen.tr("hidden_skill").getString(), r.level, ClientState.progress.level(r.node) >= r.level ? "✓" : "×")); }
        UiTheme.tooltip(g, lines, x, y, width, height);
    }
    private void drawLine(GuiGraphics g, double x1, double y1, double x2, double y2, boolean learned) {
        double dx = x2 - x1, dy = y2 - y1, t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy}, q = {x1 - left, width - x1, y1 - top, height - 29 - y1};
        for (int i = 0; i < 4; i++) { if (p[i] == 0) { if (q[i] < 0) return; } else { double t = q[i] / p[i]; if (p[i] < 0) t0 = Math.max(t0, t); else t1 = Math.min(t1, t); } }
        if (t0 > t1) return;
        double ax = x1 + dx * t0, ay = y1 + dy * t0, bx = x1 + dx * t1, by = y1 + dy * t1;
        g.pose().pushPose(); g.pose().translate(ax, ay, 0); g.pose().mulPose(Axis.ZP.rotation((float)Math.atan2(by - ay, bx - ax)));
        UiTheme.draw(g, learned ? "line_learned" : "line_locked", 0, -UiTheme.metric("lineWidth") / 2, Math.max(1, (int)Math.ceil(Math.hypot(bx - ax, by - ay))), UiTheme.metric("lineWidth")); g.pose().popPose();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && x < left && y >= top && y < height - 75) {
            int i = (int)((y - top - 4) / 24) + categoryScroll; List<String> list = categories(); if (i >= 0 && i < list.size()) { category = list.get(i); selected.clear(); } return true;
        }
        if (!canvas(x, y)) return false;
        if (button == 0 && !query.isBlank()) {
            List<String> found = results(); int i = (int)((y - top) / 23) + searchOffset;
            if (i >= 0 && i < found.size()) { var n = data().nodes.get(found.get(i)); category = n.category; view().x = -n.x * view().scale; view().y = -n.y * view().scale; selected.clear(); selected.add(found.get(i)); search.setValue(""); return true; }
        }
        startX = x; startY = y;
        if (button == 2) { panning = true; return true; }
        String id = hit(x, y);
        if (editing && button == 1) {
            if (id == null && data().categories.containsKey(category)) { id = "node_" + UUID.randomUUID().toString().replace("-", ""); var n = new Definition.Node(); n.name = FormScreen.tr("new_node").getString(); n.category = category; n.x = wx(x); n.y = wy(y); data().nodes.put(id, n); selected.clear(); selected.add(id); touch(); }
            else if (id != null && !selected.contains(id)) { selected.clear(); selected.add(id); }
            if (!selected.isEmpty()) EditorScreens.node(this, selected); return true;
        }
        if (button == 0 && id != null) {
            if (!editing) { EditorScreens.detail(this, id); return true; }
            if (hasControlDown()) { if (!selected.add(id)) selected.remove(id); }
            else if (!selected.contains(id)) { selected.clear(); selected.add(id); }
            moving = selected.contains(id); return true;
        }
        if (editing && button == 0) { if (!hasControlDown()) selected.clear(); boxing = true; return true; }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (panning) { view().x += dx; view().y += dy; return true; }
        if (moving) { for (String id : selected) { var n = data().nodes.get(id); if (n != null) { n.x += dx / view().scale; n.y += dy / view().scale; } } dirty = true; return true; }
        if (boxing) return true; return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (boxing) data().nodes.forEach((id, n) -> { if (n.category.equals(category) && sx(n.x) >= Math.min(startX, x) && sx(n.x) <= Math.max(startX, x) && sy(n.y) >= Math.min(startY, y) && sy(n.y) <= Math.max(startY, y)) selected.add(id); });
        panning = moving = boxing = false; return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (x < left) { categoryScroll -= (int)delta; return true; }
        if (canvas(x, y) && !query.isBlank()) { searchOffset -= (int)delta; return true; }
        if (!canvas(x, y)) return super.mouseScrolled(x, y, delta);
        double oldX = wx(x), oldY = wy(y); view().scale = Math.max(0.25, Math.min(3, view().scale * Math.pow(1.15, delta)));
        view().x += x - sx(oldX); view().y += y - sy(oldY); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused()) return super.keyPressed(key, scan, modifiers);
        if (editing && hasControlDown() && key == GLFW.GLFW_KEY_C) { clipboard.copy(data(), selected); status = FormScreen.tr("copied"); return true; }
        if (editing && hasControlDown() && key == GLFW.GLFW_KEY_V) { pastePrompt(); return true; }
        if (editing && key == GLFW.GLFW_KEY_DELETE && !selected.isEmpty()) { deletePrompt(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    private void pastePrompt() {
        double x = wx(mouseX), y = wy(mouseY); String target = category;
        minecraft.setScreen(new UiConfirmScreen(keep -> { minecraft.setScreen(this); selected.clear(); selected.addAll(clipboard.paste(data(), target, x, y, keep)); touch(); }, FormScreen.tr("paste"), FormScreen.tr("paste_external"), FormScreen.tr("keep_external"), FormScreen.tr("internal_only")) {
            @Override public boolean keyPressed(int key, int scan, int modifiers) { if (key == GLFW.GLFW_KEY_ESCAPE) { minecraft.setScreen(AtlasScreen.this); return true; } return super.keyPressed(key, scan, modifiers); }
        });
    }
    private void deletePrompt() {
        minecraft.setScreen(new UiConfirmScreen(ok -> { minecraft.setScreen(this); if (ok) { selected.forEach(data().nodes::remove); data().nodes.values().forEach(n -> n.prerequisites.removeIf(r -> selected.contains(r.node))); data().lines.removeIf(l -> selected.contains(l.from) || selected.contains(l.to)); selected.clear(); touch(); } }, FormScreen.tr("delete"), FormScreen.tr("delete_confirm")));
    }
    private void publish() {
        List<String> errors;
        try { errors = AtlasLogic.validate(data()); } catch (Exception ex) { status = Component.literal(ex.getMessage()); return; }
        if (!errors.isEmpty()) { status = Component.literal(errors.get(0)); return; }
        JsonObject o = new JsonObject(); o.add("definition", Definition.JSON.toJsonTree(data())); o.addProperty("revision", data().revision); publishing = true; ClientState.request("publish", o);
    }
    private void closePrompt(boolean exit) {
        minecraft.setScreen(new FormScreen(this, "unsaved") {
            @Override protected void build() {
                action("save_close", () -> { JsonObject o = new JsonObject(); o.add("definition", Definition.JSON.toJsonTree(data())); pendingClose = exit; ClientState.request("save_draft", o); });
                action("discard_close", () -> { dirty = false; editing = false; draft = null; if (exit) minecraft.setScreen(null); else minecraft.setScreen(AtlasScreen.this); });
            }
            @Override protected void save() {}
        });
    }
    @Override public void onClose() { if (editing && dirty) closePrompt(true); else minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
}
