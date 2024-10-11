package ru.tecon.queryBasedDAS.counter;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.uploaderService.model.DataModel;

import java.util.List;

/**
 * Интерфейс расширения возможностей счетчиков.
 * Поддерживается возможность получения мгновенных данных
 *
 * @author Maksim Shchelkonogov
 * 27.02.2024
 */
public interface CounterAsyncRequest {

    /**
     * Загрузка мгновенных данных
     *
     * @param params модель параметров
     * @param objectName имя объекта
     * @throws DasException в случае ошибки чтения мгновенных данных
     */
    void loadInstantData(List<DataModel> params, String objectName) throws DasException;
}
