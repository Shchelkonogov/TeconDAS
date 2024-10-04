package ru.tecon.queryBasedDAS.counter;

import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import java.util.List;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
public interface Counter {

    /**
     * Получение информации про счетчик
     *
     * @return информация о счетчике
     */
    CounterInfo getCounterInfo();

    /**
     * Получение конфигурации объекта
     *
     * @param object идентификатор объекта
     * @return конфигурация объекта
     */
    Set<Config> getConfig(String object);

    /**
     * Загрузка исторических данных по объекту
     *
     * @param params список параметров для заполнения
     * @param objectName идентификатор объекта
     */
    void loadData(List<DataModel> params, String objectName);
}
