package com.hp.skillatlas.client;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public abstract class FormScreen extends Screen {
    protected final Screen parent;
    protected final Map<String, String> values = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private int contentHeight, scroll;
    private String error = "";
    private final boolean saveButton;
    private record Row(Component label, AbstractWidget widget, int offset, int height) {}
    protected FormScreen(Screen parent, String title) { super(Component.translatable("skill_atlas." + title)); this.parent = parent; this.saveButton = !Set.of("details", "levels", "links", "manage", "categories", "pools", "rules", "files", "unsaved").contains(title); }
    public static Component tr(String key) { return Component.translatable("skill_atlas." + key); }
    @Override protected final void init() {
        rows.clear(); contentHeight = 0; build();
        if (saveButton) addRenderableWidget(Button.builder(tr("save"), b -> { try { save(); minecraft.setScreen(parent); } catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); } }).bounds(width / 2 - 102, height - 27, 100, 20).build());
        addRenderableWidget(Button.builder(tr(saveButton ? "cancel" : "back"), b -> onClose()).bounds(saveButton ? width / 2 + 2 : width / 2 - 50, height - 27, 100, 20).build());
        reposition();
    }
    protected abstract void build();
    protected abstract void save();
    protected String value(String key) { return values.getOrDefault(key, ""); }
    protected int integer(String key) { return Integer.parseInt(value(key).trim()); }
    protected long number(String key) { return Long.parseLong(value(key).trim()); }
    protected double decimal(String key) { double v = Double.parseDouble(value(key).trim()); if (!Double.isFinite(v)) throw new IllegalArgumentException("Invalid number"); return v; }
    protected boolean bool(String key) { return Boolean.parseBoolean(value(key)); }
    protected void fail(Component message) { error = message.getString(); }
    private void row(String key, AbstractWidget widget, int h) { rows.add(new Row(tr(key), widget, contentHeight, h)); contentHeight += h + 17; addRenderableWidget(widget); }
    protected void text(String key, String initial) { text(key, initial, false); }
    protected void text(String key, String initial, boolean multiline) {
        values.putIfAbsent(key, initial == null ? "" : initial);
        int left = Math.max(15, width / 2 - 210), w = Math.min(420, width - 30);
        if (multiline) {
            MultiLineEditBox box = new MultiLineEditBox(font, left, 0, w, 60, tr(key), Component.empty()); box.setCharacterLimit(8192); box.setValue(values.get(key)); box.setValueListener(v -> values.put(key, v)); row(key, box, 60);
        } else {
            EditBox box = new EditBox(font, left, 0, w, 20, tr(key)); box.setMaxLength(8192); box.setValue(values.get(key)); box.setResponder(v -> values.put(key, v)); row(key, box, 20);
        }
    }
    protected void choice(String key, String initial, String... options) {
        values.putIfAbsent(key, initial);
        var button = CycleButton.<String>builder(v -> Component.translatable("skill_atlas.value." + v)).withValues(options).withInitialValue(values.get(key))
            .create(Math.max(15, width / 2 - 210), 0, Math.min(420, width - 30), 20, tr(key), (b, v) -> values.put(key, v)); row(key, button, 20);
    }
    protected void action(String key, Runnable action) {
        row(key, Button.builder(tr(key), b -> action.run()).bounds(Math.max(15, width / 2 - 210), 0, Math.min(420, width - 30), 20).build(), 20);
    }
    protected void actionText(String label, Runnable action) {
        row("entry", Button.builder(Component.literal(label), b -> action.run()).bounds(Math.max(15, width / 2 - 210), 0, Math.min(420, width - 30), 20).build(), 20);
    }
    protected void info(String key, String text) {
        Button button = Button.builder(Component.literal(text), b -> {}).bounds(Math.max(15, width / 2 - 210), 0, Math.min(420, width - 30), 20).build();
        button.active = false; row(key, button, 20);
    }
    protected void pick(String key, String initial, List<SearchListScreen.Entry> entries) {
        text(key, initial);
        action("choose_" + key, () -> minecraft.setScreen(new SearchListScreen(this, "choose_" + key, entries, id -> { values.put(key, id); minecraft.setScreen(this); })));
    }
    private void reposition() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (height - 92))));
        for (Row row : rows) { int y = 44 + row.offset - scroll; row.widget.setY(y); row.widget.visible = y >= 43 && y + row.height <= height - 45; }
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { if (super.mouseScrolled(x, y, delta)) return true; scroll -= (int)(delta * 32); reposition(); return true; }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        renderBackground(g); g.drawCenteredString(font, title, width / 2, 12, 0xffffff);
        for (Row r : rows) if (r.widget.visible) g.drawString(font, r.label, r.widget.getX(), r.widget.getY() - 11, 0xb8c7da);
        super.render(g, x, y, partial);
        if (!error.isBlank()) g.drawString(font, font.plainSubstrByWidth(error, width - 20), 10, height - 40, 0xff7777);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
