package ru.tecon.queryBasedDAS.counter.ftp;

import java.io.IOException;

/**
 * Расширения возможностей для счетчика МСТ20 работающего по ftp
 *
 * @author Maksim Shchelkonogov
 * 09.02.2024
 */
public interface FtpCounterExtension {

    /**
     * Просмотр данных файла
     *
     * @param path путь к файлу
     * @throws IOException в случае ошибки чтения
     */
    void showData(String path) throws IOException;

    /**
     * Очистка исторических файлов
     */
    void clearHistoricalFiles();
}
