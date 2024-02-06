package ru.tecon.uploaderService.ejb;

import ru.tecon.uploaderService.model.SubscribedObject;
import ru.tecon.uploaderService.model.DataModel;

import javax.ejb.Remote;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
@Remote
public interface UploaderServiceRemote {

    /**
     * Получение подписанных объектов
     *
     * @param serverNames имена типов серверов (счетчиков)
     * @return список подписанных объектов
     */
    List<SubscribedObject> getSubscribedObjects(Set<String> serverNames);

    /**
     * Загрузка в базу данных новых объектов
     *
     * @param objects новые объекты
     */
    void uploadObjects(Map<String, List<String>> objects);

    /**
     * Загрузка в базу данных конфигурации по объектам
     *
     * @param config конфигурация
     * @param objectId id объекта
     * @param objectName имя объекта
     * @return количество новых записей конфигурации в базе данных
     */
    int uploadConfig(Set<String> config, String objectId, String objectName);

    /**
     * Обновление статуса команды в базе данных
     *
     * @param status статус
     * @param requestId id запроса
     * @param objectId id объекта
     * @param messageType тип сообщения
     * @param message сообщение
     */
    void updateCommand(int status, String requestId, String objectId, String messageType, String message);

    /**
     * Загрузка модели объекта с временем последней известной записи
     *
     * @param id id объекта
     * @return список модели объекта
     */
    List<DataModel> loadObjectModelWithStartTimes(String id);

    /**
     * Загрузка измерений в базу
     *
     * @param dataModels модель с измерениями
     */
    void uploadData(List<DataModel> dataModels);

    /**
     * Асинхронная загрузка измерений в базу
     *
     * @param dataModels модель с измерениями
     */
    void uploadDataAsync(List<DataModel> dataModels);
}
