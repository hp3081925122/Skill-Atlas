package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.AtlasConfig;
import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.SearchIndex;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Layer;
import com.hp.skillatlas.client.ui.ListView;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.TextInput;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Ui;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.data.Category;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.network.ResultPacket;
import net.minecraft.util.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class MainLayer extends Layer {
    private final AtlasScreen screen;
    public CanvasView canvas;
    private CategoryBar sidebar;
    private TextInput search;
    private ListView<SearchIndex.Hit> results;
    private Button editButton;
    private Component status;
    private int statusColor = Theme.MUTED;
    private long statusUntil;
    private int resultsVersion = Integer.MIN_VALUE;
    private int resultsProgress = Integer.MIN_VALUE;
    private boolean resultsEdit;
    private String resultsQuery = "";

    public MainLayer(AtlasScreen screen) {
        this.screen = screen;
        this.dim = false;
    }

    public String query() {
        if (search == null) {
            return "";
        }
        return search.text();
    }

    @Override
    public void layout(float sw, float sh) {
        float pw = (float) Math.floor(sw * AtlasConfig.PANEL_WIDTH.get());
        float ph = (float) Math.floor(sh * AtlasConfig.PANEL_HEIGHT.get());
        pw = Math.min(sw - 4, Math.max(360, pw));
        ph = Math.min(sh - 4, Math.max(220, ph));
        x = (float) Math.floor((sw - pw) / 2.0F);
        y = (float) Math.floor((sh - ph) / 2.0F);
        w = pw;
        h = ph;
        String keep = "";
        if (search != null) {
            keep = search.text();
        }
        clear();
        float top = y + 6;
        float right = x + w - 7;
        Button close = add(Button.icon(Sprites.CLOSE, Component.translatable("skill_atlas.ui.close"), screen::onClose));
        close.bounds(right - 18, top, 18, 18);
        right -= 21;
        editButton = add(Button.icon(Sprites.EDIT, Component.translatable("skill_atlas.ui.edit_mode"), this::toggleEdit));
        editButton.active(() -> ClientAtlas.editMode);
        editButton.tip(() -> List.of(Component.translatable("skill_atlas.ui.edit_mode"), Component.translatable("skill_atlas.ui.edit_mode.desc").withStyle(s -> s.withColor(0x8A93A8))));
        editButton.bounds(right - 18, top, 18, 18);
        right -= 21;
        Button points = add(Button.icon(Sprites.STAR, Component.translatable("skill_atlas.ui.points"), () -> stack.push(new PointsDialog())));
        points.bounds(right - 18, top, 18, 18);
        right -= 21;
        Button help = add(Button.icon(Sprites.INFO, Component.translatable("skill_atlas.ui.help"), () -> stack.push(MessageDialog.help())));
        help.bounds(right - 18, top, 18, 18);
        right -= 22;
        float searchW = Math.min(160, (right - x - 120) * 0.6F);
        search = add(new TextInput(keep));
        search.max(64).placeholder(Component.translatable("skill_atlas.ui.search"));
        search.bounds(right - searchW, top + 1, searchW, 16);
        search.onChange = s -> refreshResults(true);
        search.onEnter = () -> {
            if (!results.items().isEmpty()) {
                jumpTo(results.items().get(0).node());
            }
        };
        sidebar = add(new CategoryBar(this));
        sidebar.bounds(x + 6, y + 28, 100, h - 28 - 24);
        canvas = add(new CanvasView(this));
        float cx = sidebar.x + sidebar.w + 4;
        canvas.bounds(cx, y + 28, x + w - 6 - cx, h - 28 - 24);
        results = add(new ListView<>(this::renderHit));
        results.rowHeight = 18;
        results.empty = Component.translatable("skill_atlas.ui.no_results");
        results.onClick = (hit, button) -> jumpTo(hit.node());
        results.tooltips = hit -> NodeTooltip.lines(hit.node());
        refreshResults(true);
    }

    private void refreshResults(boolean force) {
        String q = search.text();
        if (!force && q.equals(resultsQuery) && resultsVersion == ClientAtlas.version && resultsProgress == ClientAtlas.progressVersion && resultsEdit == ClientAtlas.editMode) {
            return;
        }
        resultsQuery = q;
        resultsVersion = ClientAtlas.version;
        resultsProgress = ClientAtlas.progressVersion;
        resultsEdit = ClientAtlas.editMode;
        List<SearchIndex.Hit> hits = SearchIndex.search(q, n -> ClientAtlas.editMode || AtlasLogic.visible(ClientAtlas.definition, n, ClientAtlas.progress), 200);
        results.setItems(hits);
        results.resetScroll();
        float rows = Math.max(1, Math.min(8, hits.size()));
        float rw = Math.max(search.w + 60, 180);
        results.bounds(search.x + search.w - rw, search.y + 18, rw, rows * results.rowHeight + 4);
        canvas.searchChanged();
    }

    private void renderHit(SearchIndex.Hit hit, float rx, float ry, float rw, float rh, boolean hovered) {
        SkillNode n = hit.node();
        Icons.draw(n.icon, rx + 2, ry + 2, 14);
        Category c = ClientAtlas.definition.categories.get(n.category);
        float catW = 0;
        if (c != null) {
            Component cn = Gfx.ellipsize(Names.category(c), rw * 0.35F);
            catW = Gfx.width(cn);
            Gfx.text(cn.getVisualOrderText(), rx + rw - catW - 3, ry + 5, Theme.MUTED, true);
        }
        Component name = Gfx.ellipsize(Names.node(n), rw - catW - 26);
        int color = Theme.TEXT;
        if (hovered) {
            color = 0xFFFFFFFF;
        }
        Gfx.text(name.getVisualOrderText(), rx + 20, ry + 5, color, true);
    }

    public void jumpTo(SkillNode n) {
        if (!n.category.equals(ClientAtlas.category)) {
            ClientAtlas.category = n.category;
            ClientAtlas.SELECTION.clear();
        }
        if (ClientAtlas.editMode) {
            ClientAtlas.SELECTION.clear();
            ClientAtlas.SELECTION.add(n.id);
        }
        canvas.focusNode(n);
        Ui.focus(null);
    }

    public void selectCategory(String id) {
        if (!id.equals(ClientAtlas.category)) {
            ClientAtlas.category = id;
            ClientAtlas.SELECTION.clear();
            canvas.searchChanged();
        }
    }

    private void toggleEdit() {
        if (!ClientAtlas.canEdit || ClientAtlas.locked) {
            return;
        }
        ClientAtlas.editMode = !ClientAtlas.editMode;
        ClientAtlas.SELECTION.clear();
        if (ClientAtlas.editMode) {
            status(Component.translatable("skill_atlas.ui.edit_on"), Theme.TEAL);
        } else {
            status(Component.translatable("skill_atlas.ui.edit_off"), Theme.MUTED);
        }
    }

    public void status(Component text, int color) {
        status = text;
        statusColor = color;
        statusUntil = Util.getMillis() + 5000;
    }

    public void error(Component text) {
        status(text, Theme.RED);
        Sounds.error();
    }

    public void onResult(ResultPacket r) {
        if (r.request() == ResultPacket.LEARN) {
            String node = "";
            if (!r.args().isEmpty()) {
                node = r.args().get(0);
            }
            if (r.ok()) {
                String level = "";
                if (r.args().size() > 1) {
                    level = r.args().get(1);
                }
                status(Component.translatable(r.key(), Names.nodeById(node), level), Theme.GOLD);
                Sounds.learn();
                canvas.pulse(node);
            } else {
                error(Component.translatable(r.key(), Names.nodeById(node)));
            }
        } else if (r.request() == 0 && !r.ok()) {
            error(Names.error(r.key(), r.args()));
        }
    }

    @Override
    public void render(float pt) {
        Gfx.sprite(Sprites.PANEL, x, y, w, h);
        Gfx.icon(Sprites.BOOK, x + 9, y + 9, Theme.GOLD | 0xFF000000);
        Component title = Component.translatable("skill_atlas.title");
        Category cat = ClientAtlas.definition.categories.get(ClientAtlas.category);
        if (cat != null) {
            title = Component.translatable("skill_atlas.title.category", Names.category(cat));
        }
        Gfx.text(Gfx.ellipsize(title, search.x - x - 36), x + 25, y + 11, Theme.GOLD);
        editButton.visible = ClientAtlas.canEdit;
        editButton.enabled = !ClientAtlas.locked;
        refreshResults(false);
        results.visible = !search.text().isEmpty() && (Ui.focused(search) || results.contains(Ui.mouseX, Ui.mouseY));
        for (Widget c : children) {
            if (c != results && c.visible) {
                c.render(pt);
            }
        }
        renderFooter();
        if (results.visible) {
            Gfx.clearDepth();
            results.render(pt);
        }
    }

    private void renderFooter() {
        float fy = y + h - 19;
        float fx = sidebar.x + 2;
        Category cat = ClientAtlas.definition.categories.get(ClientAtlas.category);
        if (cat != null) {
            int color = Names.poolColor(cat.pool);
            Gfx.sprite(Sprites.GEM, fx, fy + 1, 9, 9, color);
            Component pts = cat.hasNoPool() ? Component.translatable("skill_atlas.ui.experience_balance", ClientAtlas.experiencePoints())
                    : Component.translatable("skill_atlas.ui.points_value", Names.pool(cat.pool), ClientAtlas.progress.points(cat.pool));
            Gfx.text(pts, fx + 13, fy + 2, Theme.TEXT);
            fx += 13 + Gfx.width(pts);
        }
        float right = x + w - 8;
        Component zoom = Component.literal(Math.round(canvas.zoom() * 100) + "%");
        right -= Gfx.width(zoom);
        Gfx.text(zoom, right, fy + 2, Theme.MUTED);
        if (ClientAtlas.editMode) {
            Component sel = Component.translatable("skill_atlas.ui.selection", ClientAtlas.SELECTION.size(), canvas.nodeCount());
            right -= Gfx.width(sel) + 10;
            Gfx.text(sel, right, fy + 2, Theme.TEAL);
            int[] at = canvas.cursor();
            if (at != null) {
                Component pos = Component.translatable("skill_atlas.ui.cursor", at[0], at[1]);
                right -= Gfx.width(pos) + 10;
                Gfx.text(pos, right, fy + 2, Theme.MUTED);
            }
        }
        Component line = null;
        int color = statusColor;
        if (status != null && Util.getMillis() < statusUntil) {
            line = status;
        } else if (ClientAtlas.locked) {
            line = Component.translatable("skill_atlas.ui.locked");
            color = Theme.RED;
        } else if (ClientAtlas.editMode) {
            line = Component.translatable("skill_atlas.ui.edit_hint");
            color = Theme.DIM;
        } else {
            line = Component.translatable("skill_atlas.ui.view_hint");
            color = Theme.DIM;
        }
        float avail = right - fx - 20;
        if (avail > 30) {
            Gfx.text(Gfx.ellipsize(line, avail).getVisualOrderText(), fx + 10, fy + 2, color, true);
        }
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (com.hp.skillatlas.client.ui.Ui.hasControlDown() && key == GLFW.GLFW_KEY_F) {
            Ui.focus(search);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (Ui.focus != null) {
                Ui.focus(null);
                return true;
            }
            if (ClientAtlas.editMode && !ClientAtlas.SELECTION.isEmpty()) {
                ClientAtlas.SELECTION.clear();
                return true;
            }
            return false;
        }
        if (Ui.focus != null) {
            return false;
        }
        return canvas.key(key, scan, mods);
    }

    @Override
    public boolean escape() {
        return false;
    }
}
