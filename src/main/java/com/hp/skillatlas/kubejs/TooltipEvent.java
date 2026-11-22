package com.hp.skillatlas.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.*;

public final class TooltipEvent implements KubeEvent {
    public final Player player;
    public final String nodeId, categoryId;
    public final int level, maxLevel;
    public final long revision;
    public final boolean hints;
    private final List<Component> baseLines, lines = new ArrayList<>();
    private boolean open = true;
    private int characters;
    public TooltipEvent(Player player, String nodeId, String categoryId, int level, int maxLevel, long revision, boolean hints, List<Component> base) {
        this.player = player; this.nodeId = nodeId; this.categoryId = categoryId; this.level = level; this.maxLevel = maxLevel; this.revision = revision; this.hints = hints;
        baseLines = base.stream().map(Component::copy).map(c -> (Component)c).toList();
    }
    public void add(Component line) {
        if (!open) throw new IllegalStateException("Tooltip event is closed");
        Objects.requireNonNull(line); int length = line.getString().length();
        if (lines.size() >= 32 || length > 2048 || characters + length > 16384) throw new IllegalArgumentException("Tooltip additions are too large");
        characters += length; lines.add(line.copy());
    }
    public List<Component> getBaseLines() { return baseLines.stream().map(Component::copy).map(c -> (Component)c).toList(); }
    public List<Component> finish() { open = false; return lines.stream().map(Component::copy).map(c -> (Component)c).toList(); }
    public void close() { open = false; }
}
