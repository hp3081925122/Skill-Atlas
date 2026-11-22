package com.hp.skillatlas.kubejs.client;

import com.hp.skillatlas.client.ClientAtlas;
import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.kubejs.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class ClientTooltips {
    private record Cached(int version, long time, List<Component> lines) {}
    private static final LinkedHashMap<String, Cached> CACHE = new LinkedHashMap<>();
    private static long ticks, disabledUntil;
    private static boolean evaluating;
    private ClientTooltips() {}
    public static boolean evaluating() { return evaluating; }
    public static void tick() { ticks++; }
    public static List<Component> append(SkillNode node, boolean hints, List<Component> original) {
        if (evaluating || ticks < disabledUntil || !AtlasClientEvents.TOOLTIP.hasListeners()) return original;
        String key = node.id + (hints ? ":hover" : ":details"); Cached cached = CACHE.get(key);
        if (cached == null || cached.version != ClientAtlas.version || ticks - cached.time >= 20) {
            TooltipEvent event = new TooltipEvent(Minecraft.getInstance().player, node.id, node.category, ClientAtlas.progress.level(node.id), node.maxLevel(), ClientAtlas.revision, hints, original);
            List<Component> additions = List.of(); evaluating = true;
            try { if (AtlasClientEvents.post(event)) additions = event.finish(); }
            catch (Throwable error) { disabledUntil = ticks + 100; LoggerFactory.getLogger("skill_atlas_kubejs").error("Client tooltip callback failed for node={}", node.id, error); }
            finally { event.close(); evaluating = false; }
            cached = new Cached(ClientAtlas.version, ticks, additions); CACHE.remove(key); CACHE.put(key, cached);
            while (CACHE.size() > 128) CACHE.remove(CACHE.keySet().iterator().next());
        }
        if (cached.lines.isEmpty()) return original;
        List<Component> result = new ArrayList<>(original); for (Component line : cached.lines) result.add(line.copy()); return List.copyOf(result);
    }
    public static void invalidate() { CACHE.clear(); }
    public static void clear() { CACHE.clear(); ticks = 0; disabledUntil = 0; evaluating = false; }
}
