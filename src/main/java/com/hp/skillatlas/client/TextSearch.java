package com.hp.skillatlas.client;

import com.github.promeg.pinyinhelper.Pinyin;
import java.util.*;
import java.text.Normalizer;

public final class TextSearch {
    private TextSearch() {}
    public static String normalize(String text) { return Normalizer.normalize(text.replaceAll("§.", ""), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }
    public static String index(String text) {
        String clean = text.replaceAll("§.", ""); StringBuilder full = new StringBuilder(), initials = new StringBuilder();
        for (char c : clean.toCharArray()) { String p = Pinyin.toPinyin(c); full.append(p); initials.append(p.isEmpty() ? "" : p.substring(0, 1)); }
        return normalize(clean) + "|" + normalize(full.toString()) + "|" + normalize(initials.toString());
    }
    public static boolean matches(String index, String query) { return index.contains(normalize(query)); }
}
