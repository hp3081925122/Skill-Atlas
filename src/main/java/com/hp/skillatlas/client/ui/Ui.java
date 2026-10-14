package com.hp.skillatlas.client.ui;

public final class Ui {
    public static Widget focus;
    public static Widget capture;
    public static Widget hoverOwner;
    public static double mouseX;
    public static double mouseY;

    private Ui() {
    }

    public static void focus(Widget w) {
        if (focus == w) {
            return;
        }
        Widget old = focus;
        focus = w;
        if (old != null) {
            old.focusLost();
        }
    }

    public static boolean focused(Widget w) {
        return focus == w;
    }

    public static boolean hoverAllowed(Widget w) {
        return hoverOwner == null || hoverOwner == w || capture == w;
    }

    public static void reset() {
        focus = null;
        capture = null;
        hoverOwner = null;
    }
}
