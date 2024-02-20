package ru.tecon.queryBasedDAS.counter.ftp;

import java.io.IOException;

/**
 * Интерфейс для отображения данных с ftp МСТ20
 *
 * @author Maksim Shchelkonogov
 * 09.02.2024
 */
public interface FtpCounterTest {

    void showData(String path) throws IOException;
}
