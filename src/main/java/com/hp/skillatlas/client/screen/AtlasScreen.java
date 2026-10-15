package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.AtlasConfig;
import com.hp.skillatlas.client.ClientEvents;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.LayerStack;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.network.ResultPacket;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class AtlasScreen extends Screen {
    public final LayerStack stack = new LayerStack();
    private MainLayer main;
    private double uiScale = 1.0D;
    private double k = 1.0D;
    private int lastW;
    private int lastH;
    private int lastVirtual;

    public AtlasScreen() {
        super(Component.translatable("skill_atlas.title"));
    }

    public static AtlasScreen current() {
        if (Minecraft.getInstance().screen instanceof AtlasScreen s) {
            return s;
        }
        return null;
    }

    public MainLayer main() {
        return main;
    }

    private boolean computeScale() {
        Window win = Minecraft.getInstance().getWindow();
        int fw = Math.max(1, win.getWidth());
        int fh = Math.max(1, win.getHeight());
        int virtual = AtlasConfig.VIRTUAL_HEIGHT.get();
        if (fw == lastW && fh == lastH && virtual == lastVirtual && main != null) {
            return false;
        }
        lastW = fw;
        lastH = fh;
        lastVirtual = virtual;
        uiScale = fh / (double) virtual;
        if (fw / uiScale < 400.0D) {
            uiScale = fw / 400.0D;
        }
        k = uiScale / win.getGuiScale();
        stack.width = (float) (fw / uiScale);
        stack.height = (float) (fh / uiScale);
        return true;
    }

    @Override
    protected void init() {
        lastW = -1;
        computeScale();
        if (main == null) {
            main = new MainLayer(this);
            stack.push(main);
        } else {
            stack.resize(stack.width, stack.height);
        }
    }

    private void updateMouse() {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        double gx = mc.mouseHandler.xpos() * win.getGuiScaledWidth() / Math.max(1, win.getScreenWidth());
        double gy = mc.mouseHandler.ypos() * win.getGuiScaledHeight() / Math.max(1, win.getScreenHeight());
        Ui.mouseX = gx / k;
        Ui.mouseY = gy / k;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        if (computeScale()) {
            stack.resize(stack.width, stack.height);
        }
        updateMouse();
        g.fill(0, 0, width, height, 0x70000000);
        g.pose().pushMatrix();
        g.pose().scale((float) k, (float) k);
        Gfx.begin(g, uiScale);
        stack.render(pt);
        Gfx.end();
        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        updateMouse();
        return stack.mouseDown(Ui.mouseX, Ui.mouseY, event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        updateMouse();
        stack.mouseUp(Ui.mouseX, Ui.mouseY, event.button());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        updateMouse();
        stack.mouseDrag(Ui.mouseX, Ui.mouseY, event.button());
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        updateMouse();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        updateMouse();
        return stack.scroll(Ui.mouseX, Ui.mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (stack.key(key, event.scancode(), event.modifiers())) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (Ui.focus == null && stack.top() == main && ClientEvents.OPEN.matches(event)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return stack.codepoint(event.codepoint(), 0);
    }

    @Override
    public void tick() {
        stack.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Ui.reset();
    }

    public void onResult(ResultPacket r) {
        if (main != null) {
            main.onResult(r);
        }
    }
}
