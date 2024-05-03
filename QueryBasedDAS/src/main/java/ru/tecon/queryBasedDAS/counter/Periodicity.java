package ru.tecon.queryBasedDAS.counter;

/**
 * Перечисления описывающие доступную частоту опроса исторических данных счетчиков
 *
 * @author Maksim Shchelkonogov
 * 13.02.2024
 */
public enum Periodicity {

    DISABLED("Никогда"),
    ONE_TIME_PER_HOUR("Раз в час"),
    THREE_TIME_PER_HOUR("Три раза в час"),
    EVERY_TEN_MINUTES("Каждые 10 минут");

    private final String desc;

    Periodicity(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
