package com.hp.skillatlas.client.ui;

import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextInput extends Widget {
    private static final int PAD = 4;
    private static final int LINE = 10;

    private String text = "";
    private int cursor;
    private int anchor;
    public int maxLength = 256;
    public Predicate<String> filter;
    public Consumer<String> onChange;
    public Runnable onEnter;
    public boolean multiline;
    public Component placeholder;
    public Predicate<String> valid;
    private float scrollX;
    private float scrollY;
    private long blink;
    private boolean selecting;
    private List<int[]> lines;
    private String linesFor;
    private float linesWidth;

    public TextInput(String value) {
        this.h = 16;
        setText(value);
    }

    public TextInput multiline() {
        this.multiline = true;
        return this;
    }

    public TextInput max(int max) {
        this.maxLength = max;
        return this;
    }

    public TextInput filter(Predicate<String> filter) {
        this.filter = filter;
        return this;
    }

    public TextInput onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public TextInput placeholder(Component placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public String text() {
        return text;
    }

    public void setText(String value) {
        if (value == null) {
            value = "";
        }
        text = value;
        cursor = text.length();
        anchor = cursor;
        lines = null;
    }

    private Font font() {
        return Gfx.font();
    }

    @Override
    public boolean focusable() {
        return enabled;
    }

    @Override
    public void focusLost() {
        selecting = false;
        anchor = cursor;
    }

    private float innerWidth() {
        return w - PAD * 2;
    }

    private List<int[]> lines() {
        if (lines != null && text.equals(linesFor) && linesWidth == innerWidth()) {
            return lines;
        }
        List<int[]> out = new ArrayList<>();
        float width = Math.max(10, innerWidth() - 2);
        int start = 0;
        while (true) {
            int nl = text.indexOf('\n', start);
            int end = text.length();
            if (nl >= 0) {
                end = nl;
            }
            int s = start;
            if (s == end) {
                out.add(new int[]{s, end});
            }
            while (s < end) {
                String rest = text.substring(s, end);
                int fit = font().plainSubstrByWidth(rest, (int) width).length();
                if (fit <= 0) {
                    fit = 1;
                }
                if (fit < rest.length()) {
                    int space = rest.lastIndexOf(' ', fit);
                    if (space > 0) {
                        fit = space + 1;
                    }
                }
                out.add(new int[]{s, s + fit});
                s += fit;
            }
            if (nl < 0) {
                break;
            }
            start = nl + 1;
        }
        lines = out;
        linesFor = text;
        linesWidth = innerWidth();
        return out;
    }

    private int lineOf(int index) {
        List<int[]> ls = lines();
        for (int i = 0; i < ls.size(); i++) {
            int[] l = ls.get(i);
            if (index >= l[0] && index < l[1]) {
                return i;
            }
            if (index == l[1]) {
                boolean nextStarts = i + 1 < ls.size() && ls.get(i + 1)[0] == index;
                if (!nextStarts) {
                    return i;
                }
            }
        }
        return ls.size() - 1;
    }

    private int indexAt(String s, float px) {
        if (px <= 0) {
            return 0;
        }
        int i = font().plainSubstrByWidth(s, (int) px).length();
        if (i < s.length()) {
            float a = font().width(s.substring(0, i));
            float b = font().width(s.substring(0, i + 1));
            if (px > (a + b) / 2.0F) {
                i++;
            }
        }
        return i;
    }

    private int indexAtMouse(double mx, double my) {
        if (!multiline) {
            return indexAt(text, (float) (mx - x - PAD + scrollX));
        }
        List<int[]> ls = lines();
        int li = (int) Math.floor((my - y - PAD + scrollY) / LINE);
        li = Math.max(0, Math.min(ls.size() - 1, li));
        int[] l = ls.get(li);
        return l[0] + indexAt(text.substring(l[0], l[1]), (float) (mx - x - PAD));
    }

    @Override
    public void render(float pt) {
        boolean focused = Ui.focused(this);
        Sprites.Sprite bg = Sprites.INPUT;
        if (focused) {
            bg = Sprites.INPUT_FOCUS;
        }
        Gfx.sprite(bg, x, y, w, h);
        Gfx.pushClip(x + 2, y + 1, w - 4, h - 2);
        int color = Theme.TEXT;
        if (!enabled) {
            color = Theme.MUTED;
        } else if (valid != null && !valid.test(text)) {
            color = Theme.RED;
        }
        boolean caret = focused && ((Util.getMillis() - blink) / 500) % 2 == 0;
        int selMin = Math.min(cursor, anchor);
        int selMax = Math.max(cursor, anchor);
        if (text.isEmpty() && !focused && placeholder != null) {
            Gfx.text(placeholder.getVisualOrderText(), x + PAD, y + firstLineY(), Theme.DIM, false);
        }
        if (!multiline) {
            float cx = font().width(text.substring(0, cursor));
            float inner = innerWidth() - 1;
            if (cx - scrollX > inner) {
                scrollX = cx - inner;
            }
            if (cx < scrollX) {
                scrollX = cx;
            }
            float maxScroll = Math.max(0, font().width(text) - inner);
            scrollX = Math.max(0, Math.min(scrollX, maxScroll));
            float tx = x + PAD - scrollX;
            float ty = y + firstLineY();
            if (focused && selMin != selMax) {
                float a = font().width(text.substring(0, selMin));
                float b = font().width(text.substring(0, selMax));
                Gfx.fill(tx + a, ty - 1, b - a, 10, 0x805FD6C8);
            }
            Gfx.text(text, tx, ty, color);
            if (caret) {
                Gfx.fill(tx + cx, ty - 1, 1, 10, 0xFFFFFFFF);
            }
        } else {
            List<int[]> ls = lines();
            int cl = lineOf(cursor);
            float top = cl * LINE;
            float visible = h - PAD * 2;
            if (top - scrollY > visible - LINE) {
                scrollY = top - visible + LINE;
            }
            if (top < scrollY) {
                scrollY = top;
            }
            scrollY = Math.max(0, Math.min(scrollY, Math.max(0, ls.size() * LINE - visible)));
            for (int i = 0; i < ls.size(); i++) {
                float ly = y + PAD + i * LINE - scrollY;
                if (ly + LINE < y || ly > y + h) {
                    continue;
                }
                int[] l = ls.get(i);
                String s = text.substring(l[0], l[1]);
                if (focused && selMin != selMax && selMax > l[0] && selMin <= l[1]) {
                    int a = Math.max(selMin, l[0]) - l[0];
                    int b = Math.min(selMax, l[1]) - l[0];
                    float ax = font().width(s.substring(0, a));
                    float bx = font().width(s.substring(0, b));
                    if (selMax > l[1]) {
                        bx += 3;
                    }
                    Gfx.fill(x + PAD + ax, ly - 1, bx - ax, 10, 0x805FD6C8);
                }
                Gfx.text(s, x + PAD, ly, color);
                if (caret && i == cl) {
                    float cx = font().width(text.substring(l[0], Math.min(cursor, l[1])));
                    Gfx.fill(x + PAD + cx, ly - 1, 1, 10, 0xFFFFFFFF);
                }
            }
        }
        Gfx.popClip();
    }

    private float firstLineY() {
        if (multiline) {
            return PAD;
        }
        return (h - 8) / 2.0F;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (!enabled) {
            return true;
        }
        Ui.focus(this);
        blink = Util.getMillis();
        if (button == 1 && !multiline) {
            setText("");
            changed();
            return true;
        }
        if (button != 0) {
            return true;
        }
        cursor = indexAtMouse(mx, my);
        if (!Screen.hasShiftDown()) {
            anchor = cursor;
        }
        selecting = true;
        Ui.capture = this;
        return true;
    }

    @Override
    public void mouseDrag(double mx, double my, int button) {
        if (selecting) {
            cursor = indexAtMouse(mx, my);
        }
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        selecting = false;
    }

    @Override
    public boolean scroll(double mx, double my, double delta) {
        if (!multiline) {
            return false;
        }
        float visible = h - PAD * 2;
        float max = Math.max(0, lines().size() * LINE - visible);
        if (max <= 0) {
            return false;
        }
        scrollY = Math.max(0, Math.min(max, scrollY - (float) delta * LINE * 2));
        return true;
    }

    private void changed() {
        lines = null;
        if (onChange != null) {
            onChange.accept(text);
        }
    }

    public void insert(String s) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c == '\n' || c == '\t') {
                if (multiline && c == '\n') {
                    clean.append(c);
                } else {
                    clean.append(' ');
                }
            } else if (SharedConstants.isAllowedChatCharacter(c)) {
                clean.append(c);
            }
        }
        int selMin = Math.min(cursor, anchor);
        int selMax = Math.max(cursor, anchor);
        String add = clean.toString();
        int room = maxLength - (text.length() - (selMax - selMin));
        if (room <= 0 && add.length() > 0) {
            return;
        }
        if (add.length() > room) {
            add = add.substring(0, room);
        }
        String next = text.substring(0, selMin) + add + text.substring(selMax);
        if (filter != null && !filter.test(next)) {
            return;
        }
        text = next;
        cursor = selMin + add.length();
        anchor = cursor;
        changed();
    }

    private void delete(int dir, boolean word) {
        if (cursor != anchor) {
            insert("");
            return;
        }
        int target;
        if (word) {
            target = wordFrom(cursor, dir);
        } else {
            target = Math.max(0, Math.min(text.length(), cursor + dir));
        }
        int a = Math.min(cursor, target);
        int b = Math.max(cursor, target);
        if (a == b) {
            return;
        }
        String next = text.substring(0, a) + text.substring(b);
        if (filter != null && !filter.test(next)) {
            return;
        }
        text = next;
        cursor = a;
        anchor = a;
        changed();
    }

    private int wordFrom(int from, int dir) {
        int i = from;
        if (dir < 0) {
            while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
                i--;
            }
            while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
                i--;
            }
        } else {
            while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
        }
        return i;
    }

    private void moveTo(int index, boolean select) {
        cursor = Math.max(0, Math.min(text.length(), index));
        if (!select) {
            anchor = cursor;
        }
        blink = Util.getMillis();
    }

    private void vertical(int dir, boolean select) {
        List<int[]> ls = lines();
        int cl = lineOf(cursor);
        int target = cl + dir;
        if (target < 0) {
            moveTo(0, select);
            return;
        }
        if (target >= ls.size()) {
            moveTo(text.length(), select);
            return;
        }
        int[] cur = ls.get(cl);
        float px = font().width(text.substring(cur[0], Math.min(cursor, cur[1])));
        int[] l = ls.get(target);
        moveTo(l[0] + indexAt(text.substring(l[0], l[1]), px), select);
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (!enabled) {
            return false;
        }
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        blink = Util.getMillis();
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            anchor = 0;
            cursor = text.length();
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_X)) {
            int a = Math.min(cursor, anchor);
            int b = Math.max(cursor, anchor);
            if (a != b) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(a, b));
                if (key == GLFW.GLFW_KEY_X) {
                    insert("");
                }
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                delete(-1, ctrl);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                delete(1, ctrl);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (ctrl) {
                    moveTo(wordFrom(cursor, -1), shift);
                } else if (!shift && cursor != anchor) {
                    moveTo(Math.min(cursor, anchor), false);
                } else {
                    moveTo(cursor - 1, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (ctrl) {
                    moveTo(wordFrom(cursor, 1), shift);
                } else if (!shift && cursor != anchor) {
                    moveTo(Math.max(cursor, anchor), false);
                } else {
                    moveTo(cursor + 1, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (multiline) {
                    vertical(-1, shift);
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (multiline) {
                    vertical(1, shift);
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (multiline && !ctrl) {
                    moveTo(lines().get(lineOf(cursor))[0], shift);
                } else {
                    moveTo(0, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (multiline && !ctrl) {
                    moveTo(lines().get(lineOf(cursor))[1], shift);
                } else {
                    moveTo(text.length(), shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (multiline) {
                    insert("\n");
                } else if (onEnter != null) {
                    onEnter.run();
                }
                return true;
            }
            default -> {
                return key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB && !ctrl;
            }
        }
    }

    @Override
    public boolean chr(char c, int mods) {
        if (!enabled) {
            return false;
        }
        if (SharedConstants.isAllowedChatCharacter(c)) {
            insert(String.valueOf(c));
            blink = Util.getMillis();
            return true;
        }
        return false;
    }
}
