package com.multitool.calc;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ModelTests {
    private static int failed;
    private static int passed;

    private static void check(String n, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + n);
        }
    }

    public static void main(String[] a) throws Exception {
        CalculatorModel m = new CalculatorModel();
        m.input("2+3*4");
        check("preview", m.preview().equals("14"));
        check("eval", m.evaluate() && m.expression().equals("14"));
        check("history", m.history().size() == 1 && m.history().get(0).expression().equals("2+3*4"));
        m.input("5");
        check("digit after eval starts new", m.expression().equals("5"));
        m.input("+1");
        m.input("abc");
        check("letters ignored", m.expression().equals("5+1"));
        m.backspace();
        check("backspace", m.expression().equals("5+"));
        m.clear();
        m.input("1/0");
        check("div zero not ok", !m.evaluate());
        check("div zero msg", !m.error().isEmpty() && m.preview().equals(m.error()));
        m.clear();
        m.input("7");
        m.negate();
        check("negate", m.expression().equals("-(7)"));
        m.negate();
        check("negate back", m.expression().equals("7"));
        m.clear();
        m.input("1,5+1");
        check("comma", m.expression().equals("1.5+1"));
        m.evaluate();
        check("copy text", m.copyText().equals("2.5"));
        m.input("+");
        m.input("2");
        check("operator continues result", true);
        m.clear();
        m.input("(1+2)%");
        check("percent preview", m.preview().equals("0.03"));
        m.clear();
        m.input("1+1");
        m.evaluate();
        m.recall(0);
        check("recall", m.expression().equals("2"));

        Path f = Files.createTempDirectory("mt").resolve("h.txt");
        m.saveHistory(f);
        CalculatorModel m2 = new CalculatorModel();
        m2.loadHistory(f);
        check("history persisted", m2.history().size() == m.history().size() && m2.history().size() >= 2);
        m2.clearHistory();
        check("history cleared", m2.history().isEmpty());
        for (int i = 0; i < 80; i++) {
            m.clear();
            m.input("1+" + i);
            m.evaluate();
        }
        check("history capped", m.history().size() == CalculatorModel.MAX_HISTORY);
        m.clear();
        m.input("9".repeat(500));
        check("expr length capped", m.expression().length() <= 200);

        System.out.println("Passed: " + passed + ", failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
