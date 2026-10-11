package com.multitool.calc;

/** Ошибка разбора или вычисления выражения; сообщение безопасно показывать пользователю. */
public final class CalcException extends Exception {
    private static final long serialVersionUID = 1L;

    public CalcException(String message) {
        super(message);
    }
}
