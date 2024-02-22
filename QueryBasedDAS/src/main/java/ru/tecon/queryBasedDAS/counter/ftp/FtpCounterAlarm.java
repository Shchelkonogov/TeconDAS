package ru.tecon.queryBasedDAS.counter.ftp;

import ru.tecon.uploaderService.model.DataModel;

import java.util.List;

/**
 * Интерфейс для описания поддержки alarm в счетчиках
 *
 * @author Maksim Shchelkonogov
 * 21.02.2024
 */
public interface FtpCounterAlarm {

    /**
     * Получение данных alarm
     *
     * @param params список параметров
     * @param objectName имя объекта
     */
    void loadAlarms(List<DataModel> params, String objectName);

    /**
     * Очистка alarm
     */
    void clearAlarms();
}
