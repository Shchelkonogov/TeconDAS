package ru.tecon.uploaderService.ejb.das;

import ru.tecon.uploaderService.model.RequestData;

import javax.ejb.Remote;

/**
 * Интерфейс для реализации системой сбора данных.
 * Обработка удаленных запросов от системы загрузки данных на загрузку мгновенных данных.
 *
 * @author Maksim Shchelkonogov
 * 27.02.2024
 */
@Remote
public interface InstantDataRequestRemote {

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
