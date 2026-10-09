package com.hp.skillatlas.client;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

public final class SearchListScreen extends Screen {
    public record Entry(String id, String label) {}
    private final Screen parent;
    private final List<Entry> entries;
    private final Map<String, String> indexes = new HashMap<>();
    private final Consumer<String> selected;
    private String query = "";
    private int page;
    private final List<Button> results = new ArrayList<>();
    public SearchListScreen(Screen parent, String title, List<Entry> entries, Consumer<String> selected) {
        super(FormScreen.tr(title)); this.parent = parent; this.entries = entries; this.selected = selected;
        entries.forEach(e -> indexes.put(e.id, TextSearch.index(e.label + " " + e.id)));
    }
    @Override protected void init() {
        results.clear(); EditBox search = new EditBox(font, 20, 30, width - 40, 20, FormScreen.tr("search")); search.setMaxLength(256); search.setValue(query);
        search.setResponder(v -> { query = v; page = 0; update(); }); addRenderableWidget(search);
        int count = Math.max(1, (height - 95) / 23);
        for (int i = 0; i < count; i++) results.add(addRenderableWidget(Button.builder(Component.empty(), b -> {}).bounds(20, 59 + i * 23, width - 40, 20).build()));
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); update(); }).bounds(20, height - 28, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; update(); }).bounds(60, height - 28, 35, 20).build());
        addRenderableWidget(Button.builder(FormScreen.tr("back"), b -> onClose()).bounds(width - 110, height - 28, 90, 20).build()); update();
    }
    private void update() {
        List<Entry> filtered = entries.stream().filter(e -> TextSearch.matches(indexes.get(e.id), query)).toList();
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / results.size())));
        for (int i = 0; i < results.size(); i++) {
            Button old = results.get(i); int index = page * results.size() + i; old.visible = index < filtered.size();
            if (index >= filtered.size()) continue;
            Entry entry = filtered.get(index); removeWidget(old);
            Button button = Button.builder(Component.literal(entry.label + "  [" + entry.id + "]"), b -> selected.accept(entry.id)).bounds(old.getX(), old.getY(), old.getWidth(), 20).build();
            addRenderableWidget(button); results.set(i, button);
        }
    }
    @Override public void render(GuiGraphics g, int x, int y, float p) { renderBackground(g); g.drawCenteredString(font, title, width / 2, 12, 0xffffff); super.render(g, x, y, p); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
