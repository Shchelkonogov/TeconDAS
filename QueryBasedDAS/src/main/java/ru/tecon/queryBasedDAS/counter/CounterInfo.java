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
     * Получение типа работы счетчика
     *
     * @return тип работы счетчика
     */
    CounterType getCounterType();

    /**
     * Получение имени счетчика для отображения
     *
     * @return имя счетчика
     */
    String getCounterUserName();

    /**
     * Получение списка объектов данного счетчика
     *
     * @return список объектов
     */
    List<String> getObjects();
}
