package com.hp.skillatlas.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LevelFormula {
    public static final int MAX_EXPR_LENGTH = 512;
    public static final int MAX_DEPTH = 64;
    public static final int MAX_COST = 1_000_000_000;
    public static final double MIN_AMOUNT = -1.0E6D;
    public static final double MAX_AMOUNT = 1.0E6D;
    public static final int MIN_AMPLIFIER_DISPLAY = 1;
    public static final int MAX_AMPLIFIER_DISPLAY = 256;
    public static final int MAX_DURATION = 1_000_000;
    public static final int MAX_INTERVAL = 72_000;

    public static final class FormulaException extends RuntimeException {
        private final String key;
        private final List<String> args;

        public FormulaException(String key, String... args) {
            super(key);
            this.key = key;
            this.args = List.of(args);
        }

        public String key() {
            return key;
        }

        public List<String> args() {
            return args;
        }
    }

    private enum TokenType {
        NUMBER, IDENT, OP, LPAREN, RPAREN, COMMA
    }

    private record Token(TokenType type, String text, double number) {
    }

    public interface Expr {
        double eval(double level, double max);
    }

    public static final class RewardTemplate {
        public RewardType type = RewardType.ATTRIBUTE;
        public String target = "minecraft:generic.attack_damage";
        public RewardOperation operation = RewardOperation.ADD;
        public String amountFormula = "lv";
        public String amplifierFormula = "1";
        public String durationFormula = "60";
        public String intervalFormula = "20";
        public boolean particles;
        public boolean icon = true;
        public String conditionFormula = "";

        public static RewardTemplate fromReward(Reward r) {
            RewardTemplate t = new RewardTemplate();
            t.type = r.type;
            t.target = r.target;
            t.operation = r.operation;
            if (r.type == RewardType.ATTRIBUTE) {
                t.amountFormula = scaleFormula(r.amount);
            }
            if (r.type == RewardType.EFFECT) {
                t.amplifierFormula = String.valueOf(r.amplifier + 1);
                t.durationFormula = String.valueOf(r.duration);
                t.intervalFormula = String.valueOf(r.interval);
                t.particles = r.particles;
                t.icon = r.icon;
            }
            return t;
        }

        public void switchType(RewardType next) {
            if (next == type) {
                return;
            }
            if (next == RewardType.COMMAND) {
                target = "";
            } else if (type == RewardType.COMMAND || next == RewardType.ATTRIBUTE || type == RewardType.ATTRIBUTE) {
                if (next == RewardType.ATTRIBUTE) {
                    target = "minecraft:generic.attack_damage";
                } else {
                    target = "minecraft:speed";
                }
            }
            type = next;
        }
    }

    public static final class ItemTemplate {
        public String item = "minecraft:diamond";
        public String nbt = "";
        public String countFormula = "lv";

        public ItemTemplate() {
        }

        public ItemTemplate(String item, String nbt, String countFormula) {
            this.item = item;
            this.nbt = nbt;
            this.countFormula = countFormula;
        }

        public static ItemTemplate fromItem(ItemCost c) {
            return new ItemTemplate(c.item, c.nbt, scaleFormula(c.count));
        }
    }

    public static final class Spec {
        public int maxLevel = 5;
        public boolean experience;
        public String costFormula = "lv";
        public final List<RewardTemplate> rewards = new ArrayList<>();
        public final List<ItemTemplate> items = new ArrayList<>();

        public static Spec fromLevels(List<SkillLevel> existing, boolean fallbackExperience) {
            Spec s = new Spec();
            if (existing == null || existing.isEmpty()) {
                s.maxLevel = 5;
                s.experience = fallbackExperience;
                s.costFormula = "lv";
                return s;
            }
            if (existing.size() > 1) {
                s.maxLevel = Math.min(SkillNode.MAX_LEVELS, existing.size());
            } else {
                s.maxLevel = 5;
            }
            SkillLevel first = existing.get(0);
            s.experience = first.usesExperience(fallbackExperience);
            s.costFormula = scaleFormula(first.cost);
            for (Reward r : first.rewards) {
                s.rewards.add(RewardTemplate.fromReward(r));
            }
            for (ItemCost c : first.items) {
                s.items.add(ItemTemplate.fromItem(c));
            }
            return s;
        }
    }

    private LevelFormula() {
    }

    private static String scaleFormula(double base) {
        if (base == 0.0D) {
            return "0";
        }
        if (base == 1.0D) {
            return "lv";
        }
        return Json.trim(base) + " * lv";
    }

    private static String scaleFormula(int base) {
        if (base == 0) {
            return "0";
        }
        if (base == 1) {
            return "lv";
        }
        return base + " * lv";
    }

    public static Expr parse(String raw) {
        if (raw == null) {
            throw new FormulaException("skill_atlas.ui.formula.err.empty");
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new FormulaException("skill_atlas.ui.formula.err.empty");
        }
        if (text.length() > MAX_EXPR_LENGTH) {
            throw new FormulaException("skill_atlas.ui.formula.err.too_long", String.valueOf(MAX_EXPR_LENGTH));
        }
        List<Token> tokens = tokenize(text);
        Parser parser = new Parser(tokens);
        Expr expr = parser.parseComparison(0);
        if (parser.hasMore()) {
            throw new FormulaException("skill_atlas.ui.formula.err.unexpected", parser.peek().text);
        }
        return expr;
    }

    public static double evalNumber(Expr expr, int level, int maxLevel) {
        double value = expr.eval(level, maxLevel);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new FormulaException("skill_atlas.ui.formula.err.invalid_result", String.valueOf(level));
        }
        return value;
    }

    public static double evalDoubleRange(Expr expr, int level, int maxLevel, double min, double max) {
        double value = evalNumber(expr, level, maxLevel);
        if (value < min || value > max) {
            throw new FormulaException("skill_atlas.ui.formula.err.range", String.valueOf(level), Json.trim(min), Json.trim(max));
        }
        return value;
    }

    public static int evalIntRange(Expr expr, int level, int maxLevel, int min, int max) {
        double value = evalNumber(expr, level, maxLevel);
        long rounded = Math.round(value);
        if (rounded < min || rounded > max) {
            throw new FormulaException("skill_atlas.ui.formula.err.range", String.valueOf(level), String.valueOf(min), String.valueOf(max));
        }
        return (int) rounded;
    }

    public static int evalItemCount(Expr expr, int level, int maxLevel) {
        double value = evalNumber(expr, level, maxLevel);
        long rounded = Math.round(value);
        if (rounded <= 0L) {
            return 0;
        }
        if (rounded > ItemCost.MAX_COUNT) {
            throw new FormulaException("skill_atlas.ui.formula.err.range", String.valueOf(level), "0", String.valueOf(ItemCost.MAX_COUNT));
        }
        return (int) rounded;
    }

    public static int evalEffectLevel(Expr expr, int level, int maxLevel) {
        double value = evalNumber(expr, level, maxLevel);
        long rounded = Math.round(value);
        if (rounded <= 0L) {
            return 0;
        }
        if (rounded > MAX_AMPLIFIER_DISPLAY) {
            throw new FormulaException("skill_atlas.ui.formula.err.range", String.valueOf(level), "0", String.valueOf(MAX_AMPLIFIER_DISPLAY));
        }
        return (int) rounded;
    }

    public static boolean evalCondition(Expr expr, int level, int maxLevel) {
        if (expr == null) {
            return true;
        }
        double value = evalNumber(expr, level, maxLevel);
        return Math.abs(value) > 1.0E-9D;
    }

    public static void validateCommandTemplate(String template) {
        if (template == null) {
            throw new FormulaException("skill_atlas.error.empty_command");
        }
        String trimmed = template.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            throw new FormulaException("skill_atlas.error.empty_command");
        }
        int i = 0;
        int len = trimmed.length();
        while (i < len) {
            if (trimmed.charAt(i) == '$' && i + 1 < len && trimmed.charAt(i + 1) == '{') {
                int end = trimmed.indexOf('}', i + 2);
                if (end < 0) {
                    throw new FormulaException("skill_atlas.ui.formula.err.unclosed_placeholder");
                }
                parse(trimmed.substring(i + 2, end));
                i = end + 1;
            } else {
                i++;
            }
        }
    }

    public static String formatCommand(String template, int level, int maxLevel) {
        if (template == null) {
            throw new FormulaException("skill_atlas.error.empty_command");
        }
        String trimmed = template.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            throw new FormulaException("skill_atlas.error.empty_command");
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        int len = trimmed.length();
        while (i < len) {
            if (trimmed.charAt(i) == '$' && i + 1 < len && trimmed.charAt(i + 1) == '{') {
                int end = trimmed.indexOf('}', i + 2);
                if (end < 0) {
                    throw new FormulaException("skill_atlas.ui.formula.err.unclosed_placeholder");
                }
                String body = trimmed.substring(i + 2, end);
                Expr expr = parse(body);
                double val = evalNumber(expr, level, maxLevel);
                out.append(formatCompact(val));
                i = end + 1;
            } else {
                out.append(trimmed.charAt(i));
                i++;
            }
        }
        String result = out.toString().trim();
        if (result.isEmpty()) {
            throw new FormulaException("skill_atlas.error.empty_command");
        }
        if (result.length() > 1024) {
            throw new FormulaException("skill_atlas.ui.formula.err.too_long", "1024");
        }
        return result;
    }

    public static String formatCompact(double value) {
        double clean = Math.round(value * 10000.0D) / 10000.0D;
        if (Math.abs(clean) < 1.0E-9D) {
            return "0";
        }
        return Json.trim(clean);
    }

    public static List<SkillLevel> generate(Spec spec) {
        if (spec.maxLevel < 1 || spec.maxLevel > SkillNode.MAX_LEVELS) {
            throw new FormulaException("skill_atlas.ui.formula.err.range", "1", "1", String.valueOf(SkillNode.MAX_LEVELS));
        }
        int max = spec.maxLevel;
        Expr costExpr = parse(spec.costFormula);
        List<CompiledReward> compiledRewards = new ArrayList<>();
        for (RewardTemplate rt : spec.rewards) {
            compiledRewards.add(compileReward(rt));
        }
        List<CompiledItem> compiledItems = new ArrayList<>();
        for (ItemTemplate it : spec.items) {
            String itemId = Json.normalizeResource(it.item);
            if (!Json.validResource(itemId)) {
                throw new FormulaException("skill_atlas.error.bad_resource", "item", it.item);
            }
            compiledItems.add(new CompiledItem(itemId, it.nbt, parse(it.countFormula)));
        }
        List<SkillLevel> out = new ArrayList<>(max);
        for (int lv = 1; lv <= max; lv++) {
            SkillLevel level = new SkillLevel();
            level.experience = spec.experience;
            level.cost = evalIntRange(costExpr, lv, max, 0, MAX_COST);
            for (CompiledReward cr : compiledRewards) {
                Reward built = cr.build(lv, max);
                if (built != null) {
                    level.rewards.add(built);
                }
            }
            for (CompiledItem ci : compiledItems) {
                int count = evalItemCount(ci.countExpr, lv, max);
                if (count > 0) {
                    level.items.add(new ItemCost(ci.item, count, ci.nbt));
                }
            }
            out.add(level);
        }
        return out;
    }

    private record CompiledItem(String item, String nbt, Expr countExpr) {
    }

    private static final class CompiledReward {
        final RewardType type;
        final String target;
        final RewardOperation operation;
        final Expr amountExpr;
        final Expr amplifierExpr;
        final Expr durationExpr;
        final Expr intervalExpr;
        final boolean particles;
        final boolean icon;
        final Expr conditionExpr;

        CompiledReward(RewardType type, String target, RewardOperation operation, Expr amountExpr,
                       Expr amplifierExpr, Expr durationExpr, Expr intervalExpr,
                       boolean particles, boolean icon, Expr conditionExpr) {
            this.type = type;
            this.target = target;
            this.operation = operation;
            this.amountExpr = amountExpr;
            this.amplifierExpr = amplifierExpr;
            this.durationExpr = durationExpr;
            this.intervalExpr = intervalExpr;
            this.particles = particles;
            this.icon = icon;
            this.conditionExpr = conditionExpr;
        }

        Reward build(int level, int max) {
            if (!evalCondition(conditionExpr, level, max)) {
                return null;
            }
            Reward r = new Reward();
            r.type = type;
            if (type == RewardType.ATTRIBUTE) {
                r.target = target;
                r.operation = operation;
                r.amount = evalDoubleRange(amountExpr, level, max, MIN_AMOUNT, MAX_AMOUNT);
                return r;
            }
            if (type == RewardType.EFFECT) {
                int effLevel = evalEffectLevel(amplifierExpr, level, max);
                if (effLevel <= 0) {
                    return null;
                }
                r.target = target;
                r.amplifier = effLevel - 1;
                r.duration = evalIntRange(durationExpr, level, max, 1, MAX_DURATION);
                r.interval = evalIntRange(intervalExpr, level, max, 1, MAX_INTERVAL);
                r.particles = particles;
                r.icon = icon;
                return r;
            }
            if (type == RewardType.IMMUNITY) {
                r.target = target;
                return r;
            }
            r.target = formatCommand(target, level, max);
            return r;
        }
    }

    private static CompiledReward compileReward(RewardTemplate rt) {
        Expr cond = null;
        if (rt.conditionFormula != null && !rt.conditionFormula.trim().isEmpty()) {
            cond = parse(rt.conditionFormula);
        }
        if (rt.type == RewardType.ATTRIBUTE) {
            String target = Json.normalizeResource(rt.target);
            if (!Json.validResource(target)) {
                throw new FormulaException("skill_atlas.error.bad_resource", "target", rt.target);
            }
            return new CompiledReward(rt.type, target, rt.operation, parse(rt.amountFormula),
                    null, null, null, false, true, cond);
        }
        if (rt.type == RewardType.EFFECT) {
            String target = Json.normalizeResource(rt.target);
            if (!Json.validResource(target)) {
                throw new FormulaException("skill_atlas.error.bad_resource", "target", rt.target);
            }
            return new CompiledReward(rt.type, target, RewardOperation.ADD, null,
                    parse(rt.amplifierFormula), parse(rt.durationFormula), parse(rt.intervalFormula),
                    rt.particles, rt.icon, cond);
        }
        if (rt.type == RewardType.IMMUNITY) {
            String target = Json.normalizeResource(rt.target);
            if (!Json.validResource(target)) {
                throw new FormulaException("skill_atlas.error.bad_resource", "target", rt.target);
            }
            return new CompiledReward(rt.type, target, RewardOperation.ADD, null,
                    null, null, null, false, true, cond);
        }
        String cmd = rt.target;
        if (cmd == null) {
            cmd = "";
        }
        validateCommandTemplate(cmd);
        return new CompiledReward(rt.type, cmd, RewardOperation.ADD, null,
                null, null, null, false, true, cond);
    }

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if ((c >= '0' && c <= '9') || c == '.') {
                int start = i;
                int dots = 0;
                while (i < len) {
                    char ch = s.charAt(i);
                    if (ch == '.') {
                        dots++;
                        if (dots > 1) {
                            throw new FormulaException("skill_atlas.ui.formula.err.number");
                        }
                        i++;
                    } else if (ch >= '0' && ch <= '9') {
                        i++;
                    } else {
                        break;
                    }
                }
                String raw = s.substring(start, i);
                if (".".equals(raw)) {
                    throw new FormulaException("skill_atlas.ui.formula.err.number");
                }
                double value;
                try {
                    value = Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    throw new FormulaException("skill_atlas.ui.formula.err.number");
                }
                out.add(new Token(TokenType.NUMBER, raw, value));
                continue;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
                int start = i;
                while (i < len) {
                    char ch = s.charAt(i);
                    if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                String id = s.substring(start, i).toLowerCase(Locale.ROOT);
                out.add(new Token(TokenType.IDENT, id, 0.0D));
                continue;
            }
            if (i + 1 < len) {
                String two = s.substring(i, i + 2);
                if (">=".equals(two) || "<=".equals(two) || "==".equals(two) || "!=".equals(two)) {
                    out.add(new Token(TokenType.OP, two, 0.0D));
                    i += 2;
                    continue;
                }
            }
            if (c == '(') {
                out.add(new Token(TokenType.LPAREN, "(", 0.0D));
                i++;
                continue;
            }
            if (c == ')') {
                out.add(new Token(TokenType.RPAREN, ")", 0.0D));
                i++;
                continue;
            }
            if (c == ',') {
                out.add(new Token(TokenType.COMMA, ",", 0.0D));
                i++;
                continue;
            }
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^' || c == '>' || c == '<') {
                out.add(new Token(TokenType.OP, String.valueOf(c), 0.0D));
                i++;
                continue;
            }
            throw new FormulaException("skill_atlas.ui.formula.err.char", String.valueOf(c));
        }
        return out;
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        boolean hasMore() {
            return pos < tokens.size();
        }

        Token peek() {
            return tokens.get(pos);
        }

        Token next() {
            return tokens.get(pos++);
        }

        private void checkDepth(int depth) {
            if (depth > MAX_DEPTH) {
                throw new FormulaException("skill_atlas.ui.formula.err.depth");
            }
        }

        Expr parseComparison(int depth) {
            checkDepth(depth);
            Expr left = parseAddSub(depth + 1);
            while (hasMore() && peek().type == TokenType.OP && isComparison(peek().text)) {
                String op = next().text;
                Expr right = parseAddSub(depth + 1);
                Expr l = left;
                left = (lv, max) -> evalComparison(op, l.eval(lv, max), right.eval(lv, max));
            }
            return left;
        }

        private static boolean isComparison(String op) {
            return ">".equals(op) || ">=".equals(op) || "<".equals(op) || "<=".equals(op) || "==".equals(op) || "!=".equals(op);
        }

        private static double evalComparison(String op, double a, double b) {
            boolean result;
            if (">".equals(op)) {
                result = a > b;
            } else if (">=".equals(op)) {
                result = a >= b;
            } else if ("<".equals(op)) {
                result = a < b;
            } else if ("<=".equals(op)) {
                result = a <= b;
            } else if ("==".equals(op)) {
                result = Math.abs(a - b) < 1.0E-9D;
            } else {
                result = Math.abs(a - b) >= 1.0E-9D;
            }
            if (result) {
                return 1.0D;
            }
            return 0.0D;
        }

        private Expr parseAddSub(int depth) {
            checkDepth(depth);
            Expr left = parseMulDiv(depth + 1);
            while (hasMore() && peek().type == TokenType.OP && ("+".equals(peek().text) || "-".equals(peek().text))) {
                String op = next().text;
                Expr right = parseMulDiv(depth + 1);
                Expr l = left;
                if ("+".equals(op)) {
                    left = (lv, max) -> l.eval(lv, max) + right.eval(lv, max);
                } else {
                    left = (lv, max) -> l.eval(lv, max) - right.eval(lv, max);
                }
            }
            return left;
        }

        private Expr parseMulDiv(int depth) {
            checkDepth(depth);
            Expr left = parseUnary(depth + 1);
            while (hasMore() && peek().type == TokenType.OP && ("*".equals(peek().text) || "/".equals(peek().text) || "%".equals(peek().text))) {
                String op = next().text;
                Expr right = parseUnary(depth + 1);
                Expr l = left;
                if ("*".equals(op)) {
                    left = (lv, max) -> l.eval(lv, max) * right.eval(lv, max);
                } else if ("/".equals(op)) {
                    left = (lv, max) -> {
                        double rv = right.eval(lv, max);
                        if (Math.abs(rv) < 1.0E-15D) {
                            throw new FormulaException("skill_atlas.ui.formula.err.div_zero");
                        }
                        return l.eval(lv, max) / rv;
                    };
                } else {
                    left = (lv, max) -> {
                        double rv = right.eval(lv, max);
                        if (Math.abs(rv) < 1.0E-15D) {
                            throw new FormulaException("skill_atlas.ui.formula.err.div_zero");
                        }
                        return l.eval(lv, max) % rv;
                    };
                }
            }
            return left;
        }

        private Expr parseUnary(int depth) {
            checkDepth(depth);
            if (hasMore() && peek().type == TokenType.OP && ("+".equals(peek().text) || "-".equals(peek().text))) {
                String op = next().text;
                Expr inner = parseUnary(depth + 1);
                if ("-".equals(op)) {
                    return (lv, max) -> -inner.eval(lv, max);
                }
                return inner;
            }
            return parsePower(depth + 1);
        }

        private Expr parsePower(int depth) {
            checkDepth(depth);
            Expr left = parsePrimary(depth + 1);
            if (hasMore() && peek().type == TokenType.OP && "^".equals(peek().text)) {
                next();
                Expr right = parseUnary(depth + 1);
                return (lv, max) -> Math.pow(left.eval(lv, max), right.eval(lv, max));
            }
            return left;
        }

        private Expr parsePrimary(int depth) {
            checkDepth(depth);
            if (!hasMore()) {
                throw new FormulaException("skill_atlas.ui.formula.err.incomplete");
            }
            Token t = peek();
            if (t.type == TokenType.NUMBER) {
                next();
                double v = t.number;
                return (lv, max) -> v;
            }
            if (t.type == TokenType.IDENT) {
                next();
                if (hasMore() && peek().type == TokenType.LPAREN) {
                    next();
                    List<Expr> args = new ArrayList<>();
                    if (hasMore() && peek().type != TokenType.RPAREN) {
                        args.add(parseComparison(depth + 1));
                        while (hasMore() && peek().type == TokenType.COMMA) {
                            next();
                            args.add(parseComparison(depth + 1));
                        }
                    }
                    if (!hasMore() || peek().type != TokenType.RPAREN) {
                        throw new FormulaException("skill_atlas.ui.formula.err.rparen");
                    }
                    next();
                    return buildFunction(t.text, args);
                }
                return buildVariable(t.text);
            }
            if (t.type == TokenType.LPAREN) {
                next();
                Expr inner = parseComparison(depth + 1);
                if (!hasMore() || peek().type != TokenType.RPAREN) {
                    throw new FormulaException("skill_atlas.ui.formula.err.rparen");
                }
                next();
                return inner;
            }
            throw new FormulaException("skill_atlas.ui.formula.err.unexpected", t.text);
        }

        private static Expr buildVariable(String name) {
            if ("lv".equals(name) || "level".equals(name) || "x".equals(name)) {
                return (lv, max) -> lv;
            }
            if ("max".equals(name)) {
                return (lv, max) -> max;
            }
            if ("pi".equals(name)) {
                return (lv, max) -> Math.PI;
            }
            if ("e".equals(name)) {
                return (lv, max) -> Math.E;
            }
            throw new FormulaException("skill_atlas.ui.formula.err.var", name);
        }

        private static void requireArgs(String name, List<Expr> args, int expected) {
            if (args.size() != expected) {
                throw new FormulaException("skill_atlas.ui.formula.err.args", name, String.valueOf(expected));
            }
        }

        private static Expr buildFunction(String name, List<Expr> args) {
            if ("if".equals(name)) {
                requireArgs(name, args, 3);
                Expr cond = args.get(0);
                Expr yes = args.get(1);
                Expr no = args.get(2);
                return (lv, max) -> {
                    if (Math.abs(cond.eval(lv, max)) > 1.0E-9D) {
                        return yes.eval(lv, max);
                    }
                    return no.eval(lv, max);
                };
            }
            if ("sqrt".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> {
                    double v = a.eval(lv, max);
                    if (v < 0.0D) {
                        throw new FormulaException("skill_atlas.ui.formula.err.domain", "sqrt");
                    }
                    return Math.sqrt(v);
                };
            }
            if ("cbrt".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> Math.cbrt(a.eval(lv, max));
            }
            if ("pow".equals(name)) {
                requireArgs(name, args, 2);
                Expr a = args.get(0);
                Expr b = args.get(1);
                return (lv, max) -> Math.pow(a.eval(lv, max), b.eval(lv, max));
            }
            if ("exp".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> Math.exp(a.eval(lv, max));
            }
            if ("ln".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> {
                    double v = a.eval(lv, max);
                    if (v <= 0.0D) {
                        throw new FormulaException("skill_atlas.ui.formula.err.domain", "ln");
                    }
                    return Math.log(v);
                };
            }
            if ("log10".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> {
                    double v = a.eval(lv, max);
                    if (v <= 0.0D) {
                        throw new FormulaException("skill_atlas.ui.formula.err.domain", "log10");
                    }
                    return Math.log10(v);
                };
            }
            if ("log2".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> {
                    double v = a.eval(lv, max);
                    if (v <= 0.0D) {
                        throw new FormulaException("skill_atlas.ui.formula.err.domain", "log2");
                    }
                    return Math.log(v) / Math.log(2.0D);
                };
            }
            if ("log".equals(name)) {
                if (args.size() == 1) {
                    Expr a = args.get(0);
                    return (lv, max) -> {
                        double v = a.eval(lv, max);
                        if (v <= 0.0D) {
                            throw new FormulaException("skill_atlas.ui.formula.err.domain", "log");
                        }
                        return Math.log10(v);
                    };
                }
                if (args.size() == 2) {
                    Expr a = args.get(0);
                    Expr b = args.get(1);
                    return (lv, max) -> {
                        double v = a.eval(lv, max);
                        double base = b.eval(lv, max);
                        if (v <= 0.0D || base <= 0.0D || Math.abs(base - 1.0D) < 1.0E-9D) {
                            throw new FormulaException("skill_atlas.ui.formula.err.domain", "log");
                        }
                        return Math.log(v) / Math.log(base);
                    };
                }
                throw new FormulaException("skill_atlas.ui.formula.err.args", name, "1-2");
            }
            if ("floor".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> Math.floor(a.eval(lv, max));
            }
            if ("ceil".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> Math.ceil(a.eval(lv, max));
            }
            if ("round".equals(name)) {
                if (args.size() == 1) {
                    Expr a = args.get(0);
                    return (lv, max) -> (double) Math.round(a.eval(lv, max));
                }
                if (args.size() == 2) {
                    Expr a = args.get(0);
                    Expr b = args.get(1);
                    return (lv, max) -> {
                        double v = a.eval(lv, max);
                        int digits = (int) Math.round(b.eval(lv, max));
                        if (digits < 0 || digits > 6) {
                            throw new FormulaException("skill_atlas.ui.formula.err.domain", "round");
                        }
                        double scale = Math.pow(10.0D, digits);
                        return Math.round(v * scale) / scale;
                    };
                }
                throw new FormulaException("skill_atlas.ui.formula.err.args", name, "1-2");
            }
            if ("abs".equals(name)) {
                requireArgs(name, args, 1);
                Expr a = args.get(0);
                return (lv, max) -> Math.abs(a.eval(lv, max));
            }
            if ("min".equals(name)) {
                if (args.size() < 2) {
                    throw new FormulaException("skill_atlas.ui.formula.err.args", name, "2+");
                }
                List<Expr> copy = List.copyOf(args);
                return (lv, max) -> {
                    double best = copy.get(0).eval(lv, max);
                    for (int i = 1; i < copy.size(); i++) {
                        best = Math.min(best, copy.get(i).eval(lv, max));
                    }
                    return best;
                };
            }
            if ("max".equals(name)) {
                if (args.size() < 2) {
                    throw new FormulaException("skill_atlas.ui.formula.err.args", name, "2+");
                }
                List<Expr> copy = List.copyOf(args);
                return (lv, max) -> {
                    double best = copy.get(0).eval(lv, max);
                    for (int i = 1; i < copy.size(); i++) {
                        best = Math.max(best, copy.get(i).eval(lv, max));
                    }
                    return best;
                };
            }
            if ("clamp".equals(name)) {
                requireArgs(name, args, 3);
                Expr val = args.get(0);
                Expr lo = args.get(1);
                Expr hi = args.get(2);
                return (lv, max) -> {
                    double v = val.eval(lv, max);
                    double low = lo.eval(lv, max);
                    double high = hi.eval(lv, max);
                    if (low > high) {
                        throw new FormulaException("skill_atlas.ui.formula.err.domain", "clamp");
                    }
                    return Math.max(low, Math.min(high, v));
                };
            }
            throw new FormulaException("skill_atlas.ui.formula.err.func", name);
        }
    }
}
