package com.hp.skillatlas.client;

import com.github.promeg.pinyinhelper.Pinyin;
import com.hp.skillatlas.SkillAtlas;
import com.hp.skillatlas.data.Texts;

import java.util.Locale;

public final class PinyinText {
    private static boolean failed;

    private PinyinText() {
    }

    public static String normalize(String text) {
        String s = Texts.strip(text).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean hasHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    public static String[] forms(String text) {
        String lower = normalize(text);
        if (failed || !hasHan(lower)) {
            return new String[]{lower};
        }
        try {
            StringBuilder full = new StringBuilder();
            StringBuilder initials = new StringBuilder();
            for (int i = 0; i < lower.length(); i++) {
                char c = lower.charAt(i);
                if (Pinyin.isChinese(c)) {
                    String p = Pinyin.toPinyin(c).toLowerCase(Locale.ROOT);
                    full.append(p);
                    if (!p.isEmpty()) {
                        initials.append(p.charAt(0));
                    }
                } else {
                    full.append(c);
                    initials.append(c);
                }
            }
            return new String[]{lower, full.toString(), initials.toString()};
        } catch (NoClassDefFoundError | RuntimeException e) {
            failed = true;
            SkillAtlas.LOGGER.warn("Pinyin search is unavailable, falling back to plain text search: {}", e.toString());
            return new String[]{lower};
        }
    }

    public static String joined(String text) {
        return String.join("\u0001", forms(text));
    }
}
