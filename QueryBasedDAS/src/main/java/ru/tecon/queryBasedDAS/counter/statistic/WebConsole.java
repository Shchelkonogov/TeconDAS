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
     * Добавление значений в статистику (дефолтная функция объединения)
     *
     * @param key ключ
     * @param value значение
     */
    default void merge(StatKey key, @NotNull StatData value) {
        getStatistic().merge(key, value, (statData, statData2) -> {
            if (statData2.getLastValues().isEmpty() && !statData.getLastValues().isEmpty()) {
                return StatData.builder(statData2.getRemoteName(), statData2.getCounterName(), statData2.getCounter())
                        .objectName(statData2.getObjectName())
                        .startRequestTime(statData2.getStartRequestTime())
                        .endRequestTime(statData2.getEndRequestTime())
                        .requestedValue(statData2.getRequestedValues())
                        .lastValue(statData.getLastValues())
                        .lastValuesUploadTime(statData.getLastValuesUploadTime())
                        .build();
            }
            return statData2;
        });
    }

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
