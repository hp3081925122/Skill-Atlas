package com.hp.skillatlas.client.ui;

public abstract class Layer extends Group {
    public LayerStack stack;
    public boolean dim = true;
    public boolean outsideCloses;

    public abstract void layout(float screenW, float screenH);

    public boolean escape() {
        close();
        return true;
    }

    public void close() {
        if (stack != null) {
            stack.pop(this);
        }
    }

    public void onClosed() {
    }

    public boolean isOpen() {
        return stack != null && stack.layers.contains(this);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        super.mouseDown(mx, my, button);
        return true;
    }

    @Override
    public boolean scroll(double mx, double my, double delta) {
        super.scroll(mx, my, delta);
        return true;
    }

    public void renderOverlay(float pt) {
    }
}
