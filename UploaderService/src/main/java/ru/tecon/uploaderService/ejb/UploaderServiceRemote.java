package ru.tecon.uploaderService.ejb;

import ru.tecon.uploaderService.model.Config;
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
     * Получение подписанных объектов
     *
     * @param serverNames имена типов серверов (счетчиков)
     * @param lock загружать блокированные для загрузки объекты
     * @return список подписанных объектов
     */
    List<SubscribedObject> getSubscribedObjects(Set<String> serverNames, boolean lock);

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
    int uploadConfig(Set<Config> config, String objectId, String objectName);

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

    List<DataModel> loadInstantObjectModel(String id);

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

    /**
     * Асинхронная загрузка измерений в базу
     *
     * @param dataModels модель с измерениями
     * @param subscribedObject объект по которому загружаются данные
     */
    void uploadDataAsync(List<DataModel> dataModels, SubscribedObject subscribedObject);

    /**
     * Снятие блокировки загрузки с объекта
     *
     * @param subscribedObject объект для блокировки
     */
    void removeLoadObjectLock(SubscribedObject subscribedObject);

    /**
     * Загрузка мгновенных измерений в базу
     *
     * @param requestId id запроса
     * @param paramList модель с измерениями
     */
    int uploadInstantData(String requestId, List<DataModel> paramList);

    /**
     * Получение идентификатора объекта счетчика
     *
     * @param counter имя счетчика
     * @param object имя объекта
     * @return идентификатор объекта счетчика
     */
    String getCounterObjectId(String counter, String object);

    /**
     * Установить блокировку загрузки на объект
     *
     * @param subscribedObject объект для блокировки
     */
    void setLoadObjectLock(SubscribedObject subscribedObject);
}
