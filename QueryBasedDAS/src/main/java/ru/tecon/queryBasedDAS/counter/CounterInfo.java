package ru.tecon.queryBasedDAS.counter;

import java.util.List;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
public interface CounterInfo {

    /**
     * Получение имени счетчика
     *
     * @return имя счетчика
     */
    String getCounterName();

    /**
     * Получение списка объектов данного счетчика
     *
     * @return список объектов
     */
    List<String> getObjects();

    /**
     * Получение частоты опроса исторических данных счетчика
     *
     * @return частота опроса
     */
    Periodicity getPeriodicity();
}
