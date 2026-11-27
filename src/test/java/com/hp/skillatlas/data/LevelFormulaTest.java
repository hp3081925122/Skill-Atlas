package com.hp.skillatlas.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LevelFormulaTest {
    @Test
    void evaluatesArithmeticPowersRootsLogsAndFunctions() {
        assertEquals(14.0D, LevelFormula.evalNumber(LevelFormula.parse("2 + 3 * 4"), 1, 10), 1.0E-9D);
        assertEquals(512.0D, LevelFormula.evalNumber(LevelFormula.parse("2 ^ 3 ^ 2"), 1, 10), 1.0E-9D);
        assertEquals(-4.0D, LevelFormula.evalNumber(LevelFormula.parse("-2 ^ 2"), 1, 10), 1.0E-9D);
        assertEquals(4.0D, LevelFormula.evalNumber(LevelFormula.parse("(-2) ^ 2"), 1, 10), 1.0E-9D);
        assertEquals(0.25D, LevelFormula.evalNumber(LevelFormula.parse("2 ^ -2"), 1, 10), 1.0E-9D);
        assertEquals(3.0D, LevelFormula.evalNumber(LevelFormula.parse("sqrt(9)"), 1, 10), 1.0E-9D);
        assertEquals(2.0D, LevelFormula.evalNumber(LevelFormula.parse("cbrt(8)"), 1, 10), 1.0E-9D);
        assertEquals(2.0D, LevelFormula.evalNumber(LevelFormula.parse("log(100)"), 1, 10), 1.0E-9D);
        assertEquals(3.0D, LevelFormula.evalNumber(LevelFormula.parse("log(8, 2)"), 1, 10), 1.0E-9D);
        assertEquals(4.0D, LevelFormula.evalNumber(LevelFormula.parse("log2(16)"), 1, 10), 1.0E-9D);
        assertEquals(1.0D, LevelFormula.evalNumber(LevelFormula.parse("ln(e)"), 1, 10), 1.0E-9D);
        assertEquals(1.41D, LevelFormula.evalNumber(LevelFormula.parse("round(sqrt(2), 2)"), 1, 10), 1.0E-9D);
        assertEquals(10.0D, LevelFormula.evalNumber(LevelFormula.parse("clamp(lv * 3, 2, max)"), 5, 10), 1.0E-9D);
        assertEquals(3.0D, LevelFormula.evalNumber(LevelFormula.parse("min(9, lv, 5)"), 3, 10), 1.0E-9D);
        assertEquals(9.0D, LevelFormula.evalNumber(LevelFormula.parse("max(9, lv, 5)"), 3, 10), 1.0E-9D);
    }

    @Test
    void shortCircuitsIfBranchToAvoidDomainError() {
        LevelFormula.Expr expr = LevelFormula.parse("if(lv > 1, log(lv - 1, 2), 0)");
        assertEquals(0.0D, LevelFormula.evalNumber(expr, 1, 10), 1.0E-9D);
        assertEquals(0.0D, LevelFormula.evalNumber(expr, 2, 10), 1.0E-9D);
        assertEquals(2.0D, LevelFormula.evalNumber(expr, 5, 10), 1.0E-9D);
    }

    @Test
    void generatesValidLevelsWithSkippingAndCommandInterpolation() {
        LevelFormula.Spec spec = new LevelFormula.Spec();
        spec.maxLevel = 5;
        spec.experience = false;
        spec.costFormula = "round(4 * lv^1.2)";

        LevelFormula.RewardTemplate attr = new LevelFormula.RewardTemplate();
        attr.type = RewardType.ATTRIBUTE;
        attr.target = "minecraft:generic.attack_damage";
        attr.operation = RewardOperation.ADD;
        attr.amountFormula = "round(2 * sqrt(lv), 2)";
        spec.rewards.add(attr);

        LevelFormula.RewardTemplate effect = new LevelFormula.RewardTemplate();
        effect.type = RewardType.EFFECT;
        effect.target = "minecraft:haste";
        effect.amplifierFormula = "if(lv >= 3, lv - 2, 0)";
        effect.durationFormula = "60 + lv * 20";
        effect.intervalFormula = "40";
        spec.rewards.add(effect);

        LevelFormula.RewardTemplate immunity = new LevelFormula.RewardTemplate();
        immunity.type = RewardType.IMMUNITY;
        immunity.target = "minecraft:poison";
        immunity.conditionFormula = "lv >= 4";
        spec.rewards.add(immunity);

        LevelFormula.RewardTemplate cmd = new LevelFormula.RewardTemplate();
        cmd.type = RewardType.COMMAND;
        cmd.target = "/give @s minecraft:diamond_sword{Enchantments:[{id:\"sharpness\",lvl:${lv}}]} ${if(lv == max, 2, 1)}";
        cmd.conditionFormula = "lv >= 5";
        spec.rewards.add(cmd);

        spec.items.add(new LevelFormula.ItemTemplate("minecraft:diamond", "", "if(lv >= 3, lv - 2, 0)"));

        List<SkillLevel> levels = LevelFormula.generate(spec);
        assertEquals(5, levels.size());

        assertEquals(4, levels.get(0).cost);
        assertEquals(1, levels.get(0).rewards.size());
        assertEquals(2.0D, levels.get(0).rewards.get(0).amount, 1.0E-9D);
        assertTrue(levels.get(0).items.isEmpty());

        assertEquals(2, levels.get(2).rewards.size());
        assertEquals(0, levels.get(2).rewards.get(1).amplifier);
        assertEquals(120, levels.get(2).rewards.get(1).duration);
        assertEquals(1, levels.get(2).items.size());
        assertEquals(1, levels.get(2).items.get(0).count);

        assertEquals(3, levels.get(3).rewards.size());
        assertEquals(RewardType.IMMUNITY, levels.get(3).rewards.get(2).type);

        assertEquals(4, levels.get(4).rewards.size());
        assertEquals(RewardType.COMMAND, levels.get(4).rewards.get(3).type);
        assertEquals("give @s minecraft:diamond_sword{Enchantments:[{id:\"sharpness\",lvl:5}]} 2", levels.get(4).rewards.get(3).target);
        assertEquals(3, levels.get(4).items.get(0).count);

        for (SkillLevel lv : levels) {
            SkillLevel roundTrip = SkillLevel.fromJson(lv.toJson());
            assertEquals(lv.cost, roundTrip.cost);
            assertEquals(lv.rewards.size(), roundTrip.rewards.size());
            assertEquals(lv.items.size(), roundTrip.items.size());
        }
    }

    @Test
    void rejectsInvalidSyntaxAndDomainErrors() {
        assertEquals("skill_atlas.ui.formula.err.empty", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.parse("  ")).key());
        assertEquals("skill_atlas.ui.formula.err.rparen", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.parse("(lv + 1")).key());
        assertEquals("skill_atlas.ui.formula.err.var", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.parse("foo + 1")).key());
        assertEquals("skill_atlas.ui.formula.err.func", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.parse("sin(lv)")).key());
        assertEquals("skill_atlas.ui.formula.err.div_zero", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.evalNumber(LevelFormula.parse("10 / (lv - 1)"), 1, 5)).key());
        assertEquals("skill_atlas.ui.formula.err.domain", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.evalNumber(LevelFormula.parse("sqrt(-1)"), 1, 5)).key());
        assertEquals("skill_atlas.ui.formula.err.domain", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.evalNumber(LevelFormula.parse("log(0)"), 1, 5)).key());
        assertEquals("skill_atlas.ui.formula.err.domain", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.evalNumber(LevelFormula.parse("log(10, 1)"), 1, 5)).key());
        assertEquals("skill_atlas.ui.formula.err.unclosed_placeholder", assertThrows(LevelFormula.FormulaException.class, () -> LevelFormula.validateCommandTemplate("say ${lv")).key());
    }
}
