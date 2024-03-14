package ru.tecon.queryBasedDAS.counter;

/**
 * Перечисления описывающие доступную частоту опроса исторических данных счетчиков
 *
 * @author Maksim Shchelkonogov
 * 13.02.2024
 */
public enum Periodicity {

    ONE_TIME_PER_HOUR,
    THREE_TIME_PER_HOUR,
    EVERY_TEN_MINUTES
}
