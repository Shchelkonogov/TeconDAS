package ru.tecon.uploaderService.ejb.das;

import ru.tecon.uploaderService.model.RequestData;

import javax.ejb.Remote;

/**
 * Интерфейс для реализации системой сбора данных.
 * Обработка удаленных запросов от системы загрузки данных на конфигурацию объектов.
 *
 * @author Maksim Shchelkonogov
 * 16.01.2024
 */
@Remote
public interface ConfigRequestRemote {

    /**
     * Принять запрос.
     *
     * @param requestData информация по запросу
     */
    void accept(RequestData requestData);

    /**
     * Принять запрос асинхронно.
     *
     * @param requestData информация по запросу
     */
    void acceptAsync(RequestData requestData);
}
