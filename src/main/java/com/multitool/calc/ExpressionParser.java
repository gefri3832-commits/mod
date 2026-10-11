package com.multitool.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Безопасный рекурсивный парсер арифметических выражений.
 * Грамматика: expr = term {(+|-) term}; term = unary {(*|/) unary};
 * unary = [+|-] unary | postfix; postfix = primary {%}; primary = number | '(' expr ')'.
 * Никакого eval: разбираются только числа, + - * / ( ) % и десятичная точка/запятая.
 */
public final class ExpressionParser {
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final int MAX_LENGTH = 512;
    private static final int MAX_DEPTH = 64;

    private final String src;
    private int pos;
    private int depth;

    private ExpressionParser(String src) {
        this.src = src;
    }

    public static BigDecimal evaluate(String expression) throws CalcException {
        if (expression == null || expression.isBlank()) {
            throw new CalcException("Пустое выражение");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new CalcException("Выражение слишком длинное");
        }
        ExpressionParser p = new ExpressionParser(expression);
        BigDecimal v = p.parseExpr();
        p.skipWs();
        if (p.pos < p.src.length()) {
            throw new CalcException("Неожиданный символ '" + p.src.charAt(p.pos) + "' в позиции " + (p.pos + 1));
        }
        return v;
    }

    /** Форматирует результат без экспоненты и без хвостовых нулей. */
    public static String format(BigDecimal v) {
        BigDecimal r = v.round(new MathContext(15, RoundingMode.HALF_EVEN));
        if (r.signum() == 0) {
            return "0";
        }
        return r.stripTrailingZeros().toPlainString();
    }

    private BigDecimal parseExpr() throws CalcException {
        BigDecimal v = parseTerm();
        while (true) {
            skipWs();
            if (match('+')) {
                v = v.add(parseTerm(), MC);
            } else if (match('-')) {
                v = v.subtract(parseTerm(), MC);
            } else {
                return v;
            }
        }
    }

    private BigDecimal parseTerm() throws CalcException {
        BigDecimal v = parseUnary();
        while (true) {
            skipWs();
            if (match('*') || match('×')) {
                v = v.multiply(parseUnary(), MC);
            } else if (match('/') || match('÷')) {
                BigDecimal d = parseUnary();
                if (d.signum() == 0) {
                    throw new CalcException("Деление на ноль");
                }
                v = v.divide(d, MC);
            } else {
                return v;
            }
        }
    }

    private BigDecimal parseUnary() throws CalcException {
        skipWs();
        if (match('-')) {
            return parseUnary().negate();
        }
        if (match('+')) {
            return parseUnary();
        }
        return parsePostfix();
    }

    private BigDecimal parsePostfix() throws CalcException {
        BigDecimal v = parsePrimary();
        while (true) {
            skipWs();
            if (match('%')) {
                v = v.divide(BigDecimal.valueOf(100), MC);
            } else {
                return v;
            }
        }
    }

    private BigDecimal parsePrimary() throws CalcException {
        skipWs();
        if (pos >= src.length()) {
            throw new CalcException("Неожиданный конец выражения");
        }
        if (match('(')) {
            if (++depth > MAX_DEPTH) {
                throw new CalcException("Слишком глубокая вложенность скобок");
            }
            BigDecimal v = parseExpr();
            skipWs();
            if (!match(')')) {
                throw new CalcException("Не закрыта скобка");
            }
            depth--;
            return v;
        }
        char c = src.charAt(pos);
        if (Character.isDigit(c) || c == '.' || c == ',') {
            return parseNumber();
        }
        throw new CalcException("Неожиданный символ '" + c + "' в позиции " + (pos + 1));
    }

    private BigDecimal parseNumber() throws CalcException {
        int start = pos;
        boolean dot = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if ((c == '.' || c == ',') && !dot) {
                dot = true;
                pos++;
            } else {
                break;
            }
        }
        String t = src.substring(start, pos).replace(',', '.');
        if (t.equals(".")) {
            throw new CalcException("Некорректное число");
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new CalcException("Некорректное число: " + t);
        }
    }

    private boolean match(char c) {
        if (pos < src.length() && src.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }
}
