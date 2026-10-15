package com.hp.skillatlas.client.screen;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.client.Icons;
import com.hp.skillatlas.client.Names;
import com.hp.skillatlas.client.ui.Button;
import com.hp.skillatlas.client.ui.Dialog;
import com.hp.skillatlas.client.ui.Gfx;
import com.hp.skillatlas.client.ui.Label;
import com.hp.skillatlas.client.ui.ScrollGroup;
import com.hp.skillatlas.client.ui.Sounds;
import com.hp.skillatlas.client.ui.Sprites;
import com.hp.skillatlas.client.ui.Theme;
import com.hp.skillatlas.client.ui.Widget;
import com.hp.skillatlas.data.AtlasLogic;
import com.hp.skillatlas.data.Prerequisite;
import com.hp.skillatlas.data.SkillNode;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class InfoDialog extends Dialog {
    private final String nodeId;
    private int builtVersion;
    private int builtProgress;
    private int builtItems;
    private long builtExperience;

    public InfoDialog(String nodeId) {
        super(Component.translatable("skill_atlas.ui.details"), 300, 280);
        this.nodeId = nodeId;
    }

    private SkillNode node() {
        return ClientAtlas.definition.nodes.get(nodeId);
    }

    @Override
    protected void build() {
        builtVersion = ClientAtlas.version;
        builtProgress = ClientAtlas.progressVersion;
        builtItems = ClientAtlas.inventoryStamp();
        builtExperience = ClientAtlas.experiencePoints();
        SkillNode n = node();
        if (n == null) {
            Label gone = add(new Label(Component.translatable("skill_atlas.error.missing_node", nodeId), Theme.RED));
            gone.bounds(contentX(), contentY() + 4, contentW(), 12);
            footer(new Button(Component.translatable("skill_atlas.ui.ok"), this::close));
            return;
        }
        Widget header = add(new Widget() {
            @Override
            public void render(float pt) {
                SkillNode cur = node();
                if (cur == null) {
                    return;
                }
                AtlasLogic.State s = AtlasLogic.state(ClientAtlas.definition, cur, ClientAtlas.progress, ClientAtlas::hasItems, ClientAtlas.experiencePoints());
                Sprites.Sprite frame = Sprites.NODE_AVAILABLE;
                if (s == AtlasLogic.State.LOCKED) {
                    frame = Sprites.NODE_LOCKED;
                } else if (s == AtlasLogic.State.MAXED) {
                    frame = Sprites.NODE_MAXED;
                } else if (s == AtlasLogic.State.LEARNED || s == AtlasLogic.State.UPGRADABLE) {
                    frame = Sprites.NODE_LEARNED;
                }
                Gfx.sprite(frame, x, y, 30, 30);
                Icons.draw(cur.icon, x + 7, y + 7, 16);
                Gfx.text(Gfx.ellipsize(Names.node(cur), w - 40).getVisualOrderText(), x + 36, y + 5, 0xFFFFFFFF, true);
                Component state = Names.state(s);
                int level = Math.min(ClientAtlas.progress.level(cur.id), cur.maxLevel());
                float sw = Gfx.text(state, x + 36, y + 18, Names.stateColor(s));
                Gfx.text(Component.translatable("skill_atlas.ui.level", level, cur.maxLevel()), x + 36 + sw + 8, y + 18, Theme.MUTED);
            }
        });
        header.bounds(contentX(), contentY() + 2, contentW(), 32);
        ScrollGroup scroll = add(new ScrollGroup());
        scroll.bounds(contentX(), contentY() + 38, contentW(), contentH() - 38);
        List<Component> lines = NodeTooltip.details(n);
        float cy = scroll.y + 2;
        float width = scroll.innerWidth() - 2;
        boolean skipBlank = true;
        for (int i = 2; i < lines.size(); i++) {
            Component c = lines.get(i);
            if (c.getString().isEmpty()) {
                if (!skipBlank) {
                    cy += 5;
                }
                continue;
            }
            skipBlank = false;
            Label l = new Label(c, Theme.TEXT).wrapped(width);
            l.bounds(scroll.x + 2, cy, width, l.h);
            scroll.add(l);
            cy += l.h + 1;
        }
        List<Prerequisite> prerequisites = NodeTooltip.prerequisites(n);
        if (!prerequisites.isEmpty()) {
            cy += 5;
            String key = n.requireAll ? "skill_atlas.ui.requires_all" : "skill_atlas.ui.requires_any";
            Label heading = new Label(Component.translatable(key), Theme.GOLD).wrapped(width);
            heading.bounds(scroll.x + 2, cy, width, heading.h);
            scroll.add(heading);
            cy += heading.h + 1;
            for (Prerequisite pre : prerequisites) {
                Label link = new Label(NodeTooltip.prerequisite(pre), Theme.TEXT) {
                    @Override
                    public void render(float pt) {
                        if (hovered()) {
                            Gfx.fill(x, y, w, h, Theme.ROW_HOVER);
                        }
                        super.render(pt);
                    }

                    @Override
                    public boolean mouseDown(double mx, double my, int button) {
                        if (button != 0) {
                            return false;
                        }
                        SkillNode target = ClientAtlas.definition.nodes.get(pre.node);
                        if (target != null) {
                            Sounds.click();
                            AtlasScreen screen = AtlasScreen.current();
                            if (screen != null && screen.main() != null) {
                                screen.main().jumpTo(target);
                            }
                            InfoDialog.this.close();
                            stack.push(new InfoDialog(target.id));
                        }
                        return true;
                    }
                }.wrapped(width);
                link.tip(Component.translatable("skill_atlas.ui.prerequisite_jump", Names.nodeById(pre.node)));
                link.bounds(scroll.x + 2, cy, width, link.h);
                scroll.add(link);
                cy += link.h + 2;
            }
        }
        scroll.rebuilt();
        AtlasLogic.Check check = AtlasLogic.check(ClientAtlas.definition, n, ClientAtlas.progress, ClientAtlas::hasItems, ClientAtlas.experiencePoints());
        String key = "skill_atlas.ui.learn";
        if (ClientAtlas.progress.level(n.id) > 0) {
            key = "skill_atlas.ui.upgrade";
        }
        Button learn = new Button(Component.translatable(key), () -> ClientAtlas.learn(nodeId)).style(Button.Style.PRIMARY);
        learn.enabled = check == AtlasLogic.Check.OK;
        if (check != AtlasLogic.Check.OK) {
            learn.tip(Component.translatable("skill_atlas.learn." + check.name().toLowerCase(java.util.Locale.ROOT), Names.node(n)));
        }
        footer(new Button(Component.translatable("skill_atlas.ui.close"), this::close), learn);
    }

    @Override
    public void render(float pt) {
        if (builtVersion != ClientAtlas.version || builtProgress != ClientAtlas.progressVersion || builtItems != ClientAtlas.inventoryStamp() || builtExperience != ClientAtlas.experiencePoints()) {
            layout(stack.width, stack.height);
        }
        super.render(pt);
    }
}
