package com.hp.skillatlas.client;

import com.hp.skillatlas.data.SkillNode;
import com.hp.skillatlas.data.Texts;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class SearchIndex {
    private record Entry(SkillNode node, String plain, String[] name, String[] alias, String[] tooltip, String id) {
    }

    public record Hit(SkillNode node, int score) {
    }

    private static List<Entry> entries = new ArrayList<>();
    private static int builtVersion = Integer.MIN_VALUE;
    private static String builtLanguage = "";

    private SearchIndex() {
    }

    private static void ensure() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (builtVersion == ClientAtlas.version && lang.equals(builtLanguage)) {
            return;
        }
        List<Entry> out = new ArrayList<>(ClientAtlas.definition.nodes.size());
        for (SkillNode n : ClientAtlas.definition.nodes.values()) {
            String plain = Names.nodePlain(n);
            String[] tooltip = PinyinText.forms(Texts.component(n.tooltip).getString());
            out.add(new Entry(n, plain, PinyinText.forms(plain), PinyinText.forms(n.aliases), tooltip, n.id.toLowerCase()));
        }
        entries = out;
        builtVersion = ClientAtlas.version;
        builtLanguage = lang;
    }

    private static int score(String[] forms, String q, int exact, int prefix, int contains) {
        int best = 0;
        for (int i = 0; i < forms.length; i++) {
            String f = forms[i];
            if (f.isEmpty()) {
                continue;
            }
            int penalty = i * 4;
            if (f.equals(q)) {
                best = Math.max(best, exact - penalty);
            } else if (f.startsWith(q)) {
                best = Math.max(best, prefix - penalty);
            } else if (f.contains(q)) {
                best = Math.max(best, contains - penalty);
            }
        }
        return best;
    }

    public static List<Hit> search(String query, Predicate<SkillNode> filter, int limit) {
        String q = PinyinText.normalize(query);
        List<Hit> hits = new ArrayList<>();
        if (q.isEmpty()) {
            return hits;
        }
        ensure();
        for (Entry e : entries) {
            if (!filter.test(e.node)) {
                continue;
            }
            int s = score(e.name, q, 100, 80, 60);
            s = Math.max(s, score(e.alias, q, 70, 55, 45));
            if (e.id.contains(q)) {
                s = Math.max(s, 40);
            }
            s = Math.max(s, score(e.tooltip, q, 25, 22, 20));
            if (s > 0) {
                hits.add(new Hit(e.node, s));
            }
        }
        hits.sort(Comparator.comparingInt((Hit h) -> -h.score).thenComparing(h -> Names.nodePlain(h.node)));
        if (hits.size() > limit) {
            return new ArrayList<>(hits.subList(0, limit));
        }
        return hits;
    }
}
