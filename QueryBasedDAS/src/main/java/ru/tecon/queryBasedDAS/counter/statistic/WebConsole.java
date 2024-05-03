package ru.tecon.queryBasedDAS.counter.statistic;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * интерфейс описывающий поддержку web консоли
 *
 * @author Maksim Shchelkonogov
 * 29.03.2024
 */
public interface WebConsole {

    /**
     * Получение url web консоли
     *
     * @return url консоли
     */
    String getConsoleUrl();

    /**
     * Очистка статистики
     */
    void clearStatistic();

    /**
     * Добавление значений в статистику
     *
     * @param key ключ
     * @param value значение
     * @param remappingFunction функция для объединения
     */
    void merge(StatKey key, @NotNull StatData value, @NotNull BiFunction<? super StatData, ? super StatData, ? extends StatData> remappingFunction);

    /**
     * Получение статистики
     *
     * @return статистика
     */
    Map<StatKey, StatData> getStatistic();
}
