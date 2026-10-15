package com.hp.skillatlas.client.ui;

public final class Sprites {
    public record Sprite(int u, int v, int w, int h, int border) {
    }

    public static final Sprite PANEL = new Sprite(0, 0, 32, 32, 8);
    public static final Sprite DIALOG = new Sprite(34, 0, 32, 32, 8);
    public static final Sprite INSET = new Sprite(68, 0, 16, 16, 4);
    public static final Sprite TOOLTIP = new Sprite(86, 0, 16, 16, 4);
    public static final Sprite INPUT = new Sprite(104, 0, 16, 16, 3);
    public static final Sprite INPUT_FOCUS = new Sprite(122, 0, 16, 16, 3);
    public static final Sprite BADGE = new Sprite(140, 0, 8, 8, 3);
    public static final Sprite CHECK_OFF = new Sprite(150, 0, 10, 10, 0);
    public static final Sprite CHECK_ON = new Sprite(162, 0, 10, 10, 0);
    public static final Sprite GEM = new Sprite(174, 0, 9, 9, 0);
    public static final Sprite SCROLL_TRACK = new Sprite(186, 0, 6, 16, 2);
    public static final Sprite SCROLL_THUMB = new Sprite(194, 0, 6, 16, 2);
    public static final Sprite SCROLL_THUMB_HOVER = new Sprite(202, 0, 6, 16, 2);
    public static final Sprite BUTTON = new Sprite(0, 34, 16, 16, 4);
    public static final Sprite BUTTON_HOVER = new Sprite(18, 34, 16, 16, 4);
    public static final Sprite BUTTON_PRESSED = new Sprite(36, 34, 16, 16, 4);
    public static final Sprite BUTTON_DISABLED = new Sprite(54, 34, 16, 16, 4);
    public static final Sprite PRIMARY = new Sprite(72, 34, 16, 16, 4);
    public static final Sprite PRIMARY_HOVER = new Sprite(90, 34, 16, 16, 4);
    public static final Sprite PRIMARY_PRESSED = new Sprite(108, 34, 16, 16, 4);
    public static final Sprite DANGER = new Sprite(126, 34, 16, 16, 4);
    public static final Sprite DANGER_HOVER = new Sprite(144, 34, 16, 16, 4);
    public static final Sprite DANGER_PRESSED = new Sprite(162, 34, 16, 16, 4);
    public static final Sprite TAB = new Sprite(180, 34, 24, 20, 4);
    public static final Sprite TAB_HOVER = new Sprite(206, 34, 24, 20, 4);
    public static final Sprite TAB_SELECTED = new Sprite(232, 34, 24, 20, 4);
    public static final Sprite NODE_LOCKED = new Sprite(0, 56, 26, 26, 0);
    public static final Sprite NODE_AVAILABLE = new Sprite(28, 56, 26, 26, 0);
    public static final Sprite NODE_LEARNED = new Sprite(56, 56, 26, 26, 0);
    public static final Sprite NODE_MAXED = new Sprite(84, 56, 26, 26, 0);

    public static final int SEARCH = 0;
    public static final int EDIT = 1;
    public static final int PLUS = 2;
    public static final int MINUS = 3;
    public static final int CLOSE = 4;
    public static final int GEAR = 5;
    public static final int TRASH = 6;
    public static final int COPY = 7;
    public static final int PASTE = 8;
    public static final int LINK = 9;
    public static final int EYE = 10;
    public static final int EYE_OFF = 11;
    public static final int LOCK = 12;
    public static final int CHECK = 13;
    public static final int LEFT = 14;
    public static final int RIGHT = 15;
    public static final int UP = 16;
    public static final int DOWN = 17;
    public static final int HAND = 18;
    public static final int INFO = 19;
    public static final int BOOK = 20;
    public static final int TARGET = 21;
    public static final int STAR = 22;
    public static final int FOLDER = 23;

    private static final Sprite[] ICONS = new Sprite[24];

    static {
        for (int i = 0; i < ICONS.length; i++) {
            ICONS[i] = new Sprite((i % 18) * 14, 136 + (i / 18) * 14, 12, 12, 0);
        }
    }

    private Sprites() {
    }

    public static Sprite icon(int index) {
        return ICONS[index];
    }
}
