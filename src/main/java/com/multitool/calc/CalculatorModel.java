package com.multitool.calc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Состояние калькулятора без привязки к Minecraft: выражение, результат, история. */
public final class CalculatorModel {
    public record Entry(String expression, String result) {
    }

    public static final int MAX_HISTORY = 50;
    private static final int MAX_EXPR = 200;
    private static final String ALLOWED = "0123456789+-*/().,%";

    private final StringBuilder expr = new StringBuilder();
    private final List<Entry> history = new ArrayList<>();
    private String error = "";
    private boolean justEvaluated;

    public String expression() {
        return expr.toString();
    }

    public String error() {
        return error;
    }

    public List<Entry> history() {
        return List.copyOf(history);
    }

    /** Текст для строки результата: ошибка, предпросмотр или пусто. */
    public String preview() {
        if (!error.isEmpty()) {
            return error;
        }
        if (expr.length() == 0) {
            return "";
        }
        try {
            return ExpressionParser.format(ExpressionParser.evaluate(expr.toString()));
        } catch (CalcException e) {
            return "";
        }
    }

    /** Добавляет символы; недопустимые символы игнорируются. */
    public void input(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (ALLOWED.indexOf(c) < 0 || expr.length() >= MAX_EXPR) {
                continue;
            }
            boolean digitLike = Character.isDigit(c) || c == '.' || c == ',' || c == '(';
            if (justEvaluated && digitLike) {
                expr.setLength(0);
            }
            justEvaluated = false;
            error = "";
            expr.append(c == ',' ? '.' : c);
        }
    }

    public void backspace() {
        error = "";
        justEvaluated = false;
        if (expr.length() > 0) {
            expr.setLength(expr.length() - 1);
        }
    }

    public void clear() {
        expr.setLength(0);
        error = "";
        justEvaluated = false;
    }

    /** Смена знака всего выражения. */
    public void negate() {
        if (expr.length() == 0) {
            return;
        }
        error = "";
        String s = expr.toString();
        if (s.startsWith("-(") && s.endsWith(")") && balancedInside(s.substring(2, s.length() - 1))) {
            s = s.substring(2, s.length() - 1);
        } else {
            s = "-(" + s + ")";
        }
        expr.setLength(0);
        expr.append(s);
        justEvaluated = false;
    }

    /** Вычисляет выражение; при успехе результат становится выражением, запись попадает в историю. */
    public boolean evaluate() {
        if (expr.length() == 0) {
            return false;
        }
        try {
            BigDecimal v = ExpressionParser.evaluate(expr.toString());
            String r = ExpressionParser.format(v);
            addHistory(new Entry(expr.toString(), r));
            expr.setLength(0);
            expr.append(r);
            error = "";
            justEvaluated = true;
            return true;
        } catch (CalcException e) {
            error = e.getMessage();
            return false;
        }
    }

    /** Подставляет результат записи истории в выражение. */
    public void recall(int index) {
        if (index >= 0 && index < history.size()) {
            expr.setLength(0);
            expr.append(history.get(index).result());
            error = "";
            justEvaluated = true;
        }
    }

    public void clearHistory() {
        history.clear();
    }

    /** Результат для копирования: предпросмотр, иначе текущее выражение. */
    public String copyText() {
        String p = preview();
        return (error.isEmpty() && !p.isEmpty()) ? p : expr.toString();
    }

    public void loadHistory(Path file) {
        history.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0 && tab < line.length() - 1 && history.size() < MAX_HISTORY) {
                    history.add(new Entry(line.substring(0, tab), line.substring(tab + 1)));
                }
            }
        } catch (IOException | RuntimeException e) {
            history.clear();
        }
    }

    public void saveHistory(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        List<String> lines = new ArrayList<>();
        for (Entry e : history) {
            lines.add(e.expression() + "\t" + e.result());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private void addHistory(Entry e) {
        history.add(0, e);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    private static boolean balancedInside(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (--depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }
}
