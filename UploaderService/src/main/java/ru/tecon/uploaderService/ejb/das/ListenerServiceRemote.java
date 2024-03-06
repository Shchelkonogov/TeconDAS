package ru.tecon.uploaderService.ejb.das;

import ru.tecon.uploaderService.model.Listener;

import javax.ejb.Remote;

/**
 * Интерфейс для регистрации слушателей.
 * Используется для регистрации на серверах загрузки данных системы сбора данных.
 * Сервер загрузки данных через слушатель сообщает системе сбора данных о произошедших событиях.
 *
 * @author Maksim Shchelkonogov
 * 17.01.2024
 */
@Remote
public interface ListenerServiceRemote {

    /**
     * Зарегистрировать слушатель.
     *
     * @param listener информация о слушателе.
     */
    void addListener(Listener listener);

    /**
     * Проверка на существования слушателя
     * с заданным именем системы сбора данных.
     *
     * @param dasName имя системы сбора данных.
     * @param type тип слушателя
     * @return true если данный слушатель с заданным именем зарегистрирован.
     */
    boolean containsListener(String dasName, ListenerType type);

    /**
     * Удаление слушателя с заданным именем системы сбора данных
     *
     * @param dasName имя системы сбора данных
     * @param type тип слушателя
     */
    void removeListener(String dasName, ListenerType type);
}
