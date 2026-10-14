package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.AtlasConfig;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.SearchIndex;
import com.hp.skillatlas.client.ui.ContextMenu;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.AtlasDefinition;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.PlayerProgress;
import com.hp.skillatlas.data.Prerequisite;
import com.hp.skillatlas.data.SkillNode;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CanvasView extends Widget {
    private enum Mode {
        NONE, PAN, BOX, MOVE, LINK, RIGHT_PENDING, CLICK_PENDING
    }

    private record Link(SkillNode child, SkillNode parent, Prerequisite pre) {
    }

    private record Drawn(SkillNode node, float sx, float sy, float size, AtlasLogic.State state, boolean match, boolean ghost) {
    }

    public static final float MIN_ZOOM = 0.25F;
    public static final float MAX_ZOOM = 3.0F;
    public static final int SNAP = 8;
    private static final float GRID = 32;
    private static final ResourceLocation DEFAULT_BACKGROUND = SkillAtlas.id("textures/gui/canvas.png");

    private final MainLayer main;
    private Mode mode = Mode.NONE;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;
    private boolean dragged;
    private SkillNode pressNode;
    private Link pressLink;
    private boolean wasSelected;
    private boolean additive;
    private final Map<String, int[]> origins = new LinkedHashMap<>();
    private final Map<ResourceLocation, int[]> textureSizes = new HashMap<>();
    private boolean moved;
    private String lastClickNode;
    private long lastClickTime;
    private double lastClickX;
    private double lastClickY;
    private final Map<String, Long> pulses = new HashMap<>();
    private final Set<String> matches = new HashSet<>();
    private boolean searching;
    private List<SkillNode> cache = new ArrayList<>();
    private final Map<String, SkillNode> visible = new HashMap<>();
    private int cacheVersion = Integer.MIN_VALUE;
    private int cacheProgress = Integer.MIN_VALUE;
    private boolean cacheEdit;
    private String cacheCategory = "";
    private Link hoverLink;

    public CanvasView(MainLayer main) {
        this.main = main;
    }

    private ClientAtlas.View view() {
        return ClientAtlas.view(ClientAtlas.category);
    }

    public float zoom() {
        return view().zoom;
    }

    public List<SkillNode> nodes() {
        if (cacheVersion != ClientAtlas.version || cacheProgress != ClientAtlas.progressVersion || cacheEdit != ClientAtlas.editMode || !cacheCategory.equals(ClientAtlas.category)) {
            cacheVersion = ClientAtlas.version;
            cacheProgress = ClientAtlas.progressVersion;
            cacheEdit = ClientAtlas.editMode;
            cacheCategory = ClientAtlas.category;
            List<SkillNode> out = new ArrayList<>();
            visible.clear();
            AtlasDefinition d = ClientAtlas.definition;
            for (SkillNode n : d.nodes.values()) {
                if (!n.category.equals(ClientAtlas.category)) {
                    continue;
                }
                if (ClientAtlas.editMode || AtlasLogic.visible(d, n, ClientAtlas.progress)) {
                    out.add(n);
                    visible.put(n.id, n);
                }
            }
            cache = out;
        }
        return cache;
    }

    public int nodeCount() {
        return nodes().size();
    }

    private float cx() {
        return x + w / 2.0F;
    }

    private float cy() {
        return y + h / 2.0F;
    }

    public float sx(double wx) {
        ClientAtlas.View v = view();
        return (float) (cx() + (wx - v.panX) * v.zoom);
    }

    public float sy(double wy) {
        ClientAtlas.View v = view();
        return (float) (cy() + (wy - v.panY) * v.zoom);
    }

    public double wx(double screenX) {
        ClientAtlas.View v = view();
        return v.panX + (screenX - cx()) / v.zoom;
    }

    public double wy(double screenY) {
        ClientAtlas.View v = view();
        return v.panY + (screenY - cy()) / v.zoom;
    }

    public static float size(SkillNode n) {
        if (n.major) {
            return 34;
        }
        return 26;
    }

    public static int snap(double v) {
        return (int) Math.round(v / SNAP) * SNAP;
    }

    public int[] pastePoint() {
        double px = Ui.mouseX;
        double py = Ui.mouseY;
        if (!contains(px, py)) {
            px = cx();
            py = cy();
        }
        return new int[]{snap(wx(px)), snap(wy(py))};
    }

    public void fit() {
        ClientAtlas.View v = view();
        v.initialized = true;
        List<SkillNode> list = nodes();
        if (list.isEmpty() || w <= 0 || h <= 0) {
            v.panX = 0;
            v.panY = 0;
            v.zoom = 1.0F;
            return;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (SkillNode n : list) {
            float half = size(n) / 2.0F;
            minX = Math.min(minX, n.x - half);
            minY = Math.min(minY, n.y - half);
            maxX = Math.max(maxX, n.x + half);
            maxY = Math.max(maxY, n.y + half + 12);
        }
        v.panX = (minX + maxX) / 2.0F;
        v.panY = (minY + maxY) / 2.0F;
        float zx = (w - 40) / Math.max(1, maxX - minX);
        float zy = (h - 40) / Math.max(1, maxY - minY);
        v.zoom = Math.max(MIN_ZOOM, Math.min(1.5F, Math.min(zx, zy)));
    }

    public void focusNode(SkillNode n) {
        ClientAtlas.View v = ClientAtlas.view(n.category);
        v.panX = n.x;
        v.panY = n.y;
        if (v.zoom < 0.8F) {
            v.zoom = 1.0F;
        }
        v.initialized = true;
        pulse(n.id);
    }

    public void pulse(String id) {
        pulses.put(id, Util.getMillis());
    }

    public void searchChanged() {
        String q = main.query();
        matches.clear();
        searching = !q.isBlank();
        if (searching) {
            for (SearchIndex.Hit hit : SearchIndex.search(q, n -> true, Integer.MAX_VALUE)) {
                matches.add(hit.node().id);
            }
        }
    }

    private void zoomAt(double mx, double my, float target) {
        ClientAtlas.View v = view();
        float z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, target));
        double worldX = wx(mx);
        double worldY = wy(my);
        v.zoom = z;
        v.panX = (float) (worldX - (mx - cx()) / z);
        v.panY = (float) (worldY - (my - cy()) / z);
    }

    public SkillNode nodeAt(double mx, double my) {
        List<SkillNode> list = nodes();
        float z = zoom();
        for (int i = list.size() - 1; i >= 0; i--) {
            SkillNode n = list.get(i);
            float half = size(n) * z / 2.0F;
            if (Math.abs(mx - sx(n.x)) <= half && Math.abs(my - sy(n.y)) <= half) {
                return n;
            }
        }
        return null;
    }

    private Link linkAt(double mx, double my) {
        if (!ClientAtlas.editMode) {
            return null;
        }
        Link best = null;
        double bestDist = 4.0D;
        for (SkillNode n : nodes()) {
            for (Prerequisite pre : n.prerequisites) {
                SkillNode parent = visible.get(pre.node);
                if (parent == null) {
                    continue;
                }
                double d = segmentDistance(mx, my, sx(parent.x), sy(parent.y), sx(n.x), sy(n.y));
                if (d < bestDist) {
                    bestDist = d;
                    best = new Link(n, parent, pre);
                }
            }
        }
        return best;
    }

    private static double segmentDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = dx * dx + dy * dy;
        double t = 0;
        if (len > 0) {
            t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / len));
        }
        double qx = x1 + t * dx - px;
        double qy = y1 + t * dy - py;
        return Math.sqrt(qx * qx + qy * qy);
    }

    public List<SkillNode> selectedNodes() {
        List<SkillNode> out = new ArrayList<>();
        for (String id : ClientAtlas.SELECTION) {
            SkillNode n = ClientAtlas.definition.nodes.get(id);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    @Override
    public void render(float pt) {
        ClientAtlas.View v = view();
        if (!v.initialized) {
            fit();
        }
        Gfx.sprite(Sprites.INSET, x, y, w, h);
        Gfx.pushClip(x + 1, y + 1, w - 2, h - 2);
        renderBackground();
        if (ClientAtlas.editMode) {
            renderGrid();
        }
        List<SkillNode> list = nodes();
        hoverLink = null;
        if (mode == Mode.NONE && hovered() && nodeAt(Ui.mouseX, Ui.mouseY) == null) {
            hoverLink = linkAt(Ui.mouseX, Ui.mouseY);
        }
        renderLines(list);
        renderNodes(list);
        renderDrag();
        if (ClientAtlas.definition.categories.isEmpty()) {
            String key = "skill_atlas.ui.no_categories";
            if (ClientAtlas.editMode) {
                key = "skill_atlas.ui.no_categories.edit";
            }
            Gfx.textCentered(Component.translatable(key), cx(), cy() - 4, Theme.MUTED);
        } else if (list.isEmpty()) {
            String key = "skill_atlas.ui.empty_view";
            if (ClientAtlas.editMode) {
                key = "skill_atlas.ui.empty_edit";
            }
            Gfx.textCentered(Component.translatable(key), cx(), cy() - 4, Theme.MUTED);
        }
        Gfx.popClip();
    }

    private int[] textureSize(ResourceLocation tex) {
        return textureSizes.computeIfAbsent(tex, k -> {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(k);
            if (res.isEmpty()) {
                SkillAtlas.LOGGER.warn("Skill atlas background texture not found: {}", k);
                return new int[]{0, 0};
            }
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                return new int[]{img.getWidth(), img.getHeight()};
            } catch (IOException e) {
                SkillAtlas.LOGGER.warn("Failed to read skill atlas background texture {}: {}", k, e.getMessage());
                return new int[]{0, 0};
            }
        });
    }

    private void renderBackground() {
        ResourceLocation tex = DEFAULT_BACKGROUND;
        boolean tile = false;
        Category c = ClientAtlas.definition.categories.get(ClientAtlas.category);
        if (c != null && !c.background.isEmpty()) {
            ResourceLocation custom = ResourceLocation.tryParse(c.background);
            if (custom != null && textureSize(custom)[0] > 0) {
                tex = custom;
                tile = c.tile;
            }
        }
        int[] size = textureSize(tex);
        if (size[0] <= 0 || size[1] <= 0) {
            return;
        }
        float bx = x + 1;
        float by = y + 1;
        float bw = w - 2;
        float bh = h - 2;
        if (tile) {
            float tw = size[0] * 2 * zoom();
            float th = size[1] * 2 * zoom();
            while (tw < 16 || th < 16) {
                tw *= 2;
                th *= 2;
            }
            float ox = sx(0);
            float oy = sy(0);
            float startX = ox - (float) Math.ceil((ox - bx) / tw) * tw;
            float startY = oy - (float) Math.ceil((oy - by) / th) * th;
            for (float tx = startX; tx < bx + bw; tx += tw) {
                for (float ty = startY; ty < by + bh; ty += th) {
                    Gfx.texture(tex, tx, ty, tw, th, 0xFFFFFFFF);
                }
            }
            return;
        }
        float scale = Math.max(bw / size[0], bh / size[1]);
        float uw = bw / scale / size[0];
        float vh = bh / scale / size[1];
        float u0 = (1 - uw) / 2;
        float v0 = (1 - vh) / 2;
        Gfx.texture(tex, bx, by, bw, bh, u0, v0, u0 + uw, v0 + vh, 0xFFFFFFFF);
    }

    private void renderGrid() {
        float z = zoom();
        float step = GRID * z;
        if (step < 5) {
            return;
        }
        long i0 = (long) Math.floor(wx(x) / GRID);
        for (long i = i0; ; i++) {
            float gx = sx(i * GRID);
            if (gx > x + w) {
                break;
            }
            int c = Theme.GRID;
            if (Math.floorMod(i, 4) == 0) {
                c = Theme.GRID_MAJOR;
            }
            Gfx.fill(gx, y, 1, h, c);
        }
        long j0 = (long) Math.floor(wy(y) / GRID);
        for (long j = j0; ; j++) {
            float gy = sy(j * GRID);
            if (gy > y + h) {
                break;
            }
            int c = Theme.GRID;
            if (Math.floorMod(j, 4) == 0) {
                c = Theme.GRID_MAJOR;
            }
            Gfx.fill(x, gy, w, 1, c);
        }
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private void renderLines(List<SkillNode> list) {
        PlayerProgress p = ClientAtlas.progress;
        float z = zoom();
        for (SkillNode n : list) {
            for (Prerequisite pre : n.prerequisites) {
                SkillNode parent = visible.get(pre.node);
                if (parent == null) {
                    continue;
                }
                if (!pre.line && !ClientAtlas.editMode) {
                    continue;
                }
                float x1 = sx(parent.x);
                float y1 = sy(parent.y);
                float x2 = sx(n.x);
                float y2 = sy(n.y);
                if (Math.max(x1, x2) < x || Math.min(x1, x2) > x + w || Math.max(y1, y2) < y || Math.min(y1, y2) > y + h) {
                    continue;
                }
                int color;
                boolean parentDone = p.level(parent.id) > 0;
                if (!pre.line) {
                    color = Theme.LINE_HIDDEN;
                } else if (parentDone && p.level(n.id) > 0) {
                    color = Theme.LINE_DONE;
                } else if (parentDone) {
                    color = Theme.LINE_OPEN;
                } else {
                    color = Theme.LINE_LOCKED;
                }
                if (searching && !matches.contains(n.id) && !matches.contains(parent.id)) {
                    color = fade(color, 0.35F);
                }
                if (hoverLink != null && hoverLink.pre == pre) {
                    color = 0xFFFFFFFF;
                }
                arrow(x1, y1, size(parent) * z / 2.0F, x2, y2, size(n) * z / 2.0F, color, !pre.line);
            }
        }
    }

    private void arrow(float x1, float y1, float r1, float x2, float y2, float r2, int color, boolean dashed) {
        float z = zoom();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float al = Math.max(4, 6 * z);
        float aw = Math.max(2.5F, 3.5F * z);
        if (len < r1 + r2 + al + 1) {
            return;
        }
        float ux = dx / len;
        float uy = dy / len;
        float sxs = x1 + ux * r1;
        float sys = y1 + uy * r1;
        float tipX = x2 - ux * (r2 + 1);
        float tipY = y2 - uy * (r2 + 1);
        float bx = tipX - ux * al;
        float by = tipY - uy * al;
        float width = Math.max(1.2F, 2 * z);
        if (dashed) {
            float total = (float) Math.sqrt((bx - sxs) * (bx - sxs) + (by - sys) * (by - sys));
            float dash = Math.max(3, 6 * z);
            float gap = Math.max(2, 4 * z);
            for (float t = 0; t < total; t += dash + gap) {
                float e = Math.min(t + dash, total);
                Gfx.line(sxs + ux * t, sys + uy * t, sxs + ux * e, sys + uy * e, width, color);
            }
        } else {
            Gfx.line(sxs, sys, bx + ux * 0.5F, by + uy * 0.5F, width, color);
        }
        Gfx.triangle(tipX, tipY, bx - uy * aw, by + ux * aw, bx + uy * aw, by - ux * aw, color);
    }

    private static Sprites.Sprite frame(SkillNode n, AtlasLogic.State s) {
        if (n.major) {
            if (s == AtlasLogic.State.LOCKED) {
                return Sprites.MAJOR_LOCKED;
            }
            if (s == AtlasLogic.State.LEARNED || s == AtlasLogic.State.UPGRADABLE) {
                return Sprites.MAJOR_LEARNED;
            }
            if (s == AtlasLogic.State.MAXED) {
                return Sprites.MAJOR_MAXED;
            }
            return Sprites.MAJOR_AVAILABLE;
        }
        if (s == AtlasLogic.State.LOCKED) {
            return Sprites.NODE_LOCKED;
        }
        if (s == AtlasLogic.State.LEARNED || s == AtlasLogic.State.UPGRADABLE) {
            return Sprites.NODE_LEARNED;
        }
        if (s == AtlasLogic.State.MAXED) {
            return Sprites.NODE_MAXED;
        }
        return Sprites.NODE_AVAILABLE;
    }

    private void renderNodes(List<SkillNode> list) {
        AtlasDefinition d = ClientAtlas.definition;
        PlayerProgress p = ClientAtlas.progress;
        float z = zoom();
        List<Drawn> drawn = new ArrayList<>();
        for (SkillNode n : list) {
            float s = size(n) * z;
            float px = sx(n.x);
            float py = sy(n.y);
            if (px + s < x || px - s > x + w || py + s < y || py - s > y + h + 14) {
                continue;
            }
            boolean ghost = ClientAtlas.editMode && !AtlasLogic.visible(d, n, p);
            drawn.add(new Drawn(n, px, py, s, AtlasLogic.state(d, n, p, ClientAtlas::hasItems), !searching || matches.contains(n.id), ghost));
        }
        long now = Util.getMillis();
        float wave = (float) (0.5D + 0.5D * Math.sin(now / 320.0D));
        pulses.values().removeIf(t -> now - t > 900);
        for (Drawn dn : drawn) {
            SkillNode n = dn.node;
            Sprites.Sprite glow = Sprites.NODE_GLOW;
            Sprites.Sprite select = Sprites.NODE_SELECT;
            if (n.major) {
                glow = Sprites.MAJOR_GLOW;
                select = Sprites.MAJOR_SELECT;
            }
            float gs = dn.size * glow.w() / frame(n, dn.state).w();
            Long pulse = pulses.get(n.id);
            if (pulse != null) {
                float t = (now - pulse) / 900.0F;
                float ps = gs * (1.0F + t * 0.8F);
                Gfx.sprite(glow, dn.sx - ps / 2.0F, dn.sy - ps / 2.0F, ps, ps, fade(0xFFFFFFFF, 1.0F - t));
            }
            if (searching && dn.match) {
                Gfx.sprite(glow, dn.sx - gs / 2.0F, dn.sy - gs / 2.0F, gs, gs, 0xFFECC873);
            } else if (dn.match && (dn.state == AtlasLogic.State.AVAILABLE || dn.state == AtlasLogic.State.UPGRADABLE)) {
                Gfx.sprite(glow, dn.sx - gs / 2.0F, dn.sy - gs / 2.0F, gs, gs, fade(0xFFFFFFFF, 0.3F + 0.5F * wave));
            }
            if (ClientAtlas.editMode && ClientAtlas.SELECTION.contains(n.id)) {
                float ss = dn.size * select.w() / frame(n, dn.state).w();
                Gfx.sprite(select, dn.sx - ss / 2.0F, dn.sy - ss / 2.0F, ss, ss);
            }
            int tint = 0xFFFFFFFF;
            if (dn.ghost) {
                tint = 0x90FFFFFF;
            } else if (dn.state == AtlasLogic.State.UNAFFORDABLE) {
                tint = 0xFFB4B4B4;
            }
            Gfx.sprite(frame(n, dn.state), dn.sx - dn.size / 2.0F, dn.sy - dn.size / 2.0F, dn.size, dn.size, tint);
        }
        for (Drawn dn : drawn) {
            float is = 16 * zoom();
            if (dn.node.major) {
                is = 20 * zoom();
            }
            Icons.draw(dn.node.icon, dn.sx - is / 2.0F, dn.sy - is / 2.0F, is);
        }
        for (Drawn dn : drawn) {
            float inner = dn.size * 0.72F;
            if (!dn.match) {
                Gfx.fill(dn.sx - dn.size / 2.0F, dn.sy - dn.size / 2.0F, dn.size, dn.size, 0xB0101218);
            } else if (dn.state == AtlasLogic.State.LOCKED || dn.ghost) {
                Gfx.fill(dn.sx - inner / 2.0F, dn.sy - inner / 2.0F, inner, inner, 0x80101418);
            }
        }
        boolean details = z >= 0.5F;
        for (Drawn dn : drawn) {
            float half = dn.size / 2.0F;
            if (dn.state == AtlasLogic.State.LOCKED && details) {
                Gfx.icon(Sprites.LOCK, dn.sx + half - 9, dn.sy + half - 9, 9, 0xD0FFFFFF);
            }
            if (ClientAtlas.editMode && dn.node.hidden && details) {
                Gfx.icon(Sprites.EYE_OFF, dn.sx - half - 1, dn.sy - half - 1, 9, 0xE0C493FF);
            }
            if (dn.node.maxLevel() > 1 && details) {
                String text = Math.min(ClientAtlas.progress.level(dn.node.id), dn.node.maxLevel()) + "/" + dn.node.maxLevel();
                float bw = Gfx.width(text) + 5;
                Gfx.sprite(Sprites.BADGE, dn.sx + half - bw + 4, dn.sy + half - 6, bw, 10);
            }
        }
        boolean names = AtlasConfig.SHOW_NODE_NAMES.get() && z >= 0.6F;
        for (Drawn dn : drawn) {
            float half = dn.size / 2.0F;
            if (dn.node.maxLevel() > 1 && details) {
                int level = Math.min(ClientAtlas.progress.level(dn.node.id), dn.node.maxLevel());
                String text = level + "/" + dn.node.maxLevel();
                float bw = Gfx.width(text) + 5;
                int color = Theme.TEXT;
                if (dn.state == AtlasLogic.State.MAXED) {
                    color = Theme.VIOLET;
                } else if (level > 0) {
                    color = Theme.GOLD;
                }
                Gfx.text(text, dn.sx + half - bw + 6.5F, dn.sy + half - 5, color);
            }
            if (names) {
                Component name = Gfx.ellipsize(Names.node(dn.node), Math.max(60, 92 * z));
                int color = Theme.TEXT;
                if (!dn.match) {
                    color = Theme.DIM;
                } else if (dn.state == AtlasLogic.State.LOCKED || dn.ghost) {
                    color = Theme.MUTED;
                }
                float tw = Gfx.width(name);
                Gfx.text(name.getVisualOrderText(), dn.sx - tw / 2.0F, dn.sy + half + 3, color, true);
            }
        }
    }

    private void renderDrag() {
        if (mode == Mode.BOX && dragged) {
            float bx = (float) Math.min(pressX, lastX);
            float by = (float) Math.min(pressY, lastY);
            float bw = (float) Math.abs(lastX - pressX);
            float bh = (float) Math.abs(lastY - pressY);
            Gfx.fill(bx, by, bw, bh, Theme.SELECT_FILL);
            Gfx.outline(bx, by, bw, bh, 1, Theme.SELECT_EDGE);
        }
        if (mode == Mode.LINK && pressNode != null && dragged) {
            float x1 = sx(pressNode.x);
            float y1 = sy(pressNode.y);
            float r1 = size(pressNode) * zoom() / 2.0F;
            SkillNode target = nodeAt(lastX, lastY);
            int color = Theme.TEAL;
            float x2 = (float) lastX;
            float y2 = (float) lastY;
            float r2 = 0;
            if (target != null && target != pressNode) {
                x2 = sx(target.x);
                y2 = sy(target.y);
                r2 = size(target) * zoom() / 2.0F;
                if (EditActions.linkProblem(pressNode, target) != null) {
                    color = Theme.RED;
                }
            }
            arrow(x1, y1, r1, x2, y2, r2, color, false);
        }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        Ui.capture = this;
        pressX = mx;
        pressY = my;
        lastX = mx;
        lastY = my;
        dragged = false;
        moved = false;
        origins.clear();
        pressNode = nodeAt(mx, my);
        pressLink = null;
        if (pressNode == null) {
            pressLink = linkAt(mx, my);
        }
        if (button == 2) {
            mode = Mode.PAN;
            return true;
        }
        if (button == 1) {
            mode = Mode.RIGHT_PENDING;
            return true;
        }
        if (button != 0) {
            mode = Mode.NONE;
            return true;
        }
        long now = Util.getMillis();
        boolean shift = Screen.hasShiftDown();
        boolean ctrl = Screen.hasControlDown();
        if (!ClientAtlas.editMode) {
            if (pressNode != null) {
                mode = Mode.CLICK_PENDING;
            } else {
                mode = Mode.PAN;
            }
            return true;
        }
        if (pressNode == null) {
            boolean dbl = lastClickNode == null && now - lastClickTime < 300 && Math.abs(mx - lastClickX) < 4 && Math.abs(my - lastClickY) < 4;
            lastClickNode = null;
            lastClickTime = now;
            lastClickX = mx;
            lastClickY = my;
            if (dbl && !shift && !ctrl) {
                mode = Mode.NONE;
                lastClickTime = 0;
                EditActions.create(main, snap(wx(mx)), snap(wy(my)));
                return true;
            }
            mode = Mode.BOX;
            additive = shift || ctrl;
            return true;
        }
        boolean dbl = pressNode.id.equals(lastClickNode) && now - lastClickTime < 300;
        lastClickNode = pressNode.id;
        lastClickTime = now;
        if (dbl && !shift && !ctrl) {
            mode = Mode.NONE;
            lastClickTime = 0;
            ClientAtlas.SELECTION.clear();
            ClientAtlas.SELECTION.add(pressNode.id);
            NodeEditorDialog.edit(main, pressNode);
            return true;
        }
        if (shift) {
            mode = Mode.LINK;
            return true;
        }
        if (ctrl) {
            if (!ClientAtlas.SELECTION.remove(pressNode.id)) {
                ClientAtlas.SELECTION.add(pressNode.id);
            }
            mode = Mode.NONE;
            return true;
        }
        wasSelected = ClientAtlas.SELECTION.contains(pressNode.id);
        if (!wasSelected) {
            ClientAtlas.SELECTION.clear();
            ClientAtlas.SELECTION.add(pressNode.id);
        }
        for (SkillNode n : selectedNodes()) {
            if (n.category.equals(ClientAtlas.category)) {
                origins.put(n.id, new int[]{n.x, n.y});
            }
        }
        mode = Mode.MOVE;
        return true;
    }

    @Override
    public void mouseDrag(double mx, double my, int button) {
        double ddx = mx - lastX;
        double ddy = my - lastY;
        lastX = mx;
        lastY = my;
        if (!dragged && (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3)) {
            dragged = true;
        }
        ClientAtlas.View v = view();
        switch (mode) {
            case RIGHT_PENDING -> {
                if (dragged) {
                    mode = Mode.PAN;
                    v.panX -= (float) ((mx - pressX) / v.zoom);
                    v.panY -= (float) ((my - pressY) / v.zoom);
                }
            }
            case PAN -> {
                v.panX -= (float) (ddx / v.zoom);
                v.panY -= (float) (ddy / v.zoom);
            }
            case CLICK_PENDING -> {
                if (dragged) {
                    mode = Mode.PAN;
                    v.panX -= (float) ((mx - pressX) / v.zoom);
                    v.panY -= (float) ((my - pressY) / v.zoom);
                }
            }
            case MOVE -> {
                if (!dragged || pressNode == null) {
                    return;
                }
                int[] o = origins.get(pressNode.id);
                if (o == null) {
                    return;
                }
                int nx = (int) Math.round(o[0] + (mx - pressX) / v.zoom);
                int ny = (int) Math.round(o[1] + (my - pressY) / v.zoom);
                if (!Screen.hasAltDown()) {
                    nx = snap(nx);
                    ny = snap(ny);
                }
                int offX = nx - o[0];
                int offY = ny - o[1];
                for (Map.Entry<String, int[]> e : origins.entrySet()) {
                    SkillNode n = ClientAtlas.definition.nodes.get(e.getKey());
                    if (n != null) {
                        n.x = clampCoord(e.getValue()[0] + offX);
                        n.y = clampCoord(e.getValue()[1] + offY);
                    }
                }
                moved = offX != 0 || offY != 0;
            }
            default -> {
            }
        }
    }

    private static int clampCoord(int v) {
        return Math.max(-SkillNode.MAX_COORD, Math.min(SkillNode.MAX_COORD, v));
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        Mode m = mode;
        mode = Mode.NONE;
        switch (m) {
            case MOVE -> {
                if (moved) {
                    List<SkillNode> list = new ArrayList<>();
                    for (String id : origins.keySet()) {
                        SkillNode n = ClientAtlas.definition.nodes.get(id);
                        if (n != null) {
                            list.add(n);
                        }
                    }
                    EditActions.move(main, list);
                } else if (!dragged && wasSelected && pressNode != null) {
                    ClientAtlas.SELECTION.clear();
                    ClientAtlas.SELECTION.add(pressNode.id);
                }
                origins.clear();
            }
            case BOX -> {
                if (!additive) {
                    ClientAtlas.SELECTION.clear();
                }
                if (dragged) {
                    double x0 = Math.min(pressX, mx);
                    double x1 = Math.max(pressX, mx);
                    double y0 = Math.min(pressY, my);
                    double y1 = Math.max(pressY, my);
                    for (SkillNode n : nodes()) {
                        float px = sx(n.x);
                        float py = sy(n.y);
                        if (px >= x0 && px <= x1 && py >= y0 && py <= y1) {
                            ClientAtlas.SELECTION.add(n.id);
                        }
                    }
                }
            }
            case LINK -> {
                SkillNode target = nodeAt(mx, my);
                if (pressNode != null && target != null && target != pressNode) {
                    EditActions.link(main, List.of(pressNode), target);
                }
            }
            case CLICK_PENDING -> {
                if (pressNode != null && !dragged) {
                    if (Screen.hasShiftDown() || Screen.hasControlDown()) {
                        ClientAtlas.learn(pressNode.id);
                    } else {
                        main.stack.push(new InfoDialog(pressNode.id));
                    }
                }
            }
            case RIGHT_PENDING -> openMenu(mx, my);
            default -> {
            }
        }
    }

    private void openMenu(double mx, double my) {
        ContextMenu menu = new ContextMenu((float) mx, (float) my);
        SkillNode n = pressNode;
        if (!ClientAtlas.editMode) {
            if (n == null) {
                menu.add(Component.translatable("skill_atlas.ui.menu.fit"), Sprites.TARGET, this::fit);
            } else {
                String id = n.id;
                menu.add(Component.translatable("skill_atlas.ui.menu.details"), Sprites.INFO, () -> main.stack.push(new InfoDialog(id)));
                AtlasLogic.Check check = AtlasLogic.check(ClientAtlas.definition, n, ClientAtlas.progress, ClientAtlas::hasItems);
                String key = "skill_atlas.ui.learn";
                if (ClientAtlas.progress.level(n.id) > 0) {
                    key = "skill_atlas.ui.upgrade";
                }
                menu.add(Component.translatable(key), Sprites.STAR, check == AtlasLogic.Check.OK, () -> ClientAtlas.learn(id));
            }
            main.stack.push(menu);
            return;
        }
        if (n != null) {
            List<SkillNode> targets = new ArrayList<>();
            List<SkillNode> others = new ArrayList<>();
            if (ClientAtlas.SELECTION.contains(n.id)) {
                targets.addAll(selectedNodes());
            } else {
                targets.add(n);
                others.addAll(selectedNodes());
            }
            if (targets.size() > 1) {
                menu.add(Component.translatable("skill_atlas.ui.menu.batch_edit", targets.size()), Sprites.EDIT, () -> NodeEditorDialog.batch(main, targets));
            } else {
                menu.add(Component.translatable("skill_atlas.ui.menu.edit"), Sprites.EDIT, () -> NodeEditorDialog.edit(main, n));
            }
            menu.separator();
            menu.add(Component.translatable("skill_atlas.ui.menu.copy"), Sprites.COPY, () -> EditActions.copy(main, targets));
            menu.add(Component.translatable("skill_atlas.ui.menu.cut"), Sprites.COPY, () -> EditActions.cut(main, targets));
            int wxp = snap(wx(mx));
            int wyp = snap(wy(my));
            menu.add(Component.translatable("skill_atlas.ui.menu.paste"), Sprites.PASTE, EditActions.hasClipboard(), () -> EditActions.paste(main, wxp, wyp));
            menu.separator();
            boolean allMajor = true;
            boolean allHidden = true;
            for (SkillNode t : targets) {
                allMajor &= t.major;
                allHidden &= t.hidden;
            }
            String majorKey = "skill_atlas.ui.menu.major_on";
            if (allMajor) {
                majorKey = "skill_atlas.ui.menu.major_off";
            }
            String hiddenKey = "skill_atlas.ui.menu.hidden_on";
            if (allHidden) {
                hiddenKey = "skill_atlas.ui.menu.hidden_off";
            }
            boolean setMajor = !allMajor;
            boolean setHidden = !allHidden;
            menu.add(Component.translatable(majorKey), Sprites.STAR, () -> EditActions.setMajor(main, targets, setMajor));
            menu.add(Component.translatable(hiddenKey), Sprites.EYE_OFF, () -> EditActions.setHidden(main, targets, setHidden));
            if (!others.isEmpty()) {
                menu.add(Component.translatable("skill_atlas.ui.menu.link_selected", others.size()), Sprites.LINK, () -> EditActions.link(main, others, n));
            }
            menu.separator();
            menu.danger(Component.translatable("skill_atlas.ui.menu.delete", targets.size()), Sprites.TRASH, () -> EditActions.delete(main, targets));
        } else if (pressLink != null) {
            Link link = pressLink;
            String key = "skill_atlas.ui.menu.line_hide";
            if (!link.pre.line) {
                key = "skill_atlas.ui.menu.line_show";
            }
            boolean nextLine = !link.pre.line;
            menu.add(Component.translatable(key), Sprites.LINK, () -> EditActions.setLine(main, link.child, link.parent.id, nextLine));
            menu.danger(Component.translatable("skill_atlas.ui.menu.unlink"), Sprites.TRASH, () -> EditActions.unlink(main, link.child, link.parent.id));
        } else {
            int wxp = snap(wx(mx));
            int wyp = snap(wy(my));
            menu.add(Component.translatable("skill_atlas.ui.menu.create"), Sprites.PLUS, !ClientAtlas.definition.categories.isEmpty(), () -> EditActions.create(main, wxp, wyp));
            menu.add(Component.translatable("skill_atlas.ui.menu.paste"), Sprites.PASTE, EditActions.hasClipboard(), () -> EditActions.paste(main, wxp, wyp));
            menu.separator();
            menu.add(Component.translatable("skill_atlas.ui.menu.select_all"), Sprites.CHECK, !nodes().isEmpty(), this::selectAll);
            menu.add(Component.translatable("skill_atlas.ui.menu.fit"), Sprites.TARGET, this::fit);
        }
        main.stack.push(menu);
    }

    private void selectAll() {
        ClientAtlas.SELECTION.clear();
        for (SkillNode n : nodes()) {
            ClientAtlas.SELECTION.add(n.id);
        }
    }

    @Override
    public boolean scroll(double mx, double my, double delta) {
        zoomAt(mx, my, (float) (zoom() * Math.pow(1.15D, delta)));
        return true;
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_HOME) {
            fit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            zoomAt(cx(), cy(), zoom() * 1.25F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomAt(cx(), cy(), zoom() / 1.25F);
            return true;
        }
        if (!ClientAtlas.editMode) {
            return false;
        }
        boolean ctrl = Screen.hasControlDown();
        List<SkillNode> sel = selectedNodes();
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_D) {
            ClientAtlas.SELECTION.clear();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            if (!sel.isEmpty()) {
                EditActions.copy(main, sel);
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            if (!sel.isEmpty()) {
                EditActions.cut(main, sel);
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            int[] at = pastePoint();
            EditActions.paste(main, at[0], at[1]);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!sel.isEmpty()) {
                EditActions.delete(main, sel);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_F2) {
            if (sel.size() == 1) {
                NodeEditorDialog.edit(main, sel.get(0));
            } else if (sel.size() > 1) {
                NodeEditorDialog.batch(main, sel);
            }
            return true;
        }
        int dx = 0;
        int dy = 0;
        if (key == GLFW.GLFW_KEY_LEFT) {
            dx = -1;
        } else if (key == GLFW.GLFW_KEY_RIGHT) {
            dx = 1;
        } else if (key == GLFW.GLFW_KEY_UP) {
            dy = -1;
        } else if (key == GLFW.GLFW_KEY_DOWN) {
            dy = 1;
        }
        if ((dx != 0 || dy != 0) && !sel.isEmpty()) {
            int step = SNAP;
            if (Screen.hasShiftDown()) {
                step = 1;
            }
            for (SkillNode n : sel) {
                n.x = clampCoord(n.x + dx * step);
                n.y = clampCoord(n.y + dy * step);
            }
            EditActions.move(main, sel);
            return true;
        }
        return false;
    }

    @Override
    public List<Component> tooltip(double mx, double my) {
        if (mode != Mode.NONE) {
            return null;
        }
        SkillNode n = nodeAt(mx, my);
        if (n != null) {
            return NodeTooltip.lines(n);
        }
        if (hoverLink != null) {
            List<Component> out = new ArrayList<>();
            out.add(Component.translatable("skill_atlas.ui.link", Names.node(hoverLink.parent), Names.node(hoverLink.child)));
            String key = "skill_atlas.ui.link.visible";
            if (!hoverLink.pre.line) {
                key = "skill_atlas.ui.link.hidden";
            }
            out.add(Component.translatable(key).withStyle(s -> s.withColor(0x8A93A8)));
            out.add(Component.translatable("skill_atlas.ui.link.hint").withStyle(s -> s.withColor(0x8A93A8)));
            return out;
        }
        return null;
    }
}
