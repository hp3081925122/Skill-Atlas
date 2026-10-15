package com.hp.skillatlas.client.ui;

public final class Ui {
    private static boolean held(int left, int right) {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, left)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, right);
    }

    public static boolean hasShiftDown() {
        return held(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean hasAltDown() {
        return held(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean hasControlDown() {
        return net.minecraft.client.input.InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER == 8
                ? held(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER)
                : held(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

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
