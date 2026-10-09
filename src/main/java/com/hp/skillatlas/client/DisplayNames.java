package com.hp.skillatlas.client;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Function;

public final class DisplayNames {
    private record Label(String key, String english) {}
    private static final Map<String, Label> DEFAULTS = new HashMap<>();
    static {
        register("战斗", "Combat", "combat"); register("挖掘", "Mining", "mining"); register("生存", "Survival", "survival");
        register("通用", "General", "general"); register("力量", "Strength", "strength"); register("矿工", "Miner", "miner");
        register("提升攻击力", "Increase attack damage", "strength_tooltip");
    }
    private DisplayNames() {}
    private static void register(String chinese, String english, String key) {
        Label label = new Label("skill_atlas.default." + key, english); DEFAULTS.put(chinese, label);
        for (String separator : List.of(" / ", "/", " /", "/ ")) {
            DEFAULTS.put(chinese + separator + english, label); DEFAULTS.put(chinese + separator + english.toLowerCase(Locale.ROOT), label);
        }
    }
    public static String resolve(String value, Function<String, String> translation) {
        Label label = DEFAULTS.get(value); return label == null ? value : translation.apply(label.key);
    }
    public static String text(String value) { return resolve(value, key -> Component.translatable(key).getString()); }
    public static String searchText(String value) { Label label = DEFAULTS.get(value); return value + " " + text(value) + (label == null ? "" : " " + label.english); }
    public static String edited(String original, String input) { return text(original).equals(input) ? original : input; }
}
