package ru.tecon.queryBasedDAS.counter.ftp;

import java.io.IOException;

/**
 * Расширения возможностей для счетчика МСТ20 работающего по ftp
 *
 * @author Maksim Shchelkonogov
 * 09.02.2024
 */
public interface FtpCounterExtension {

    void showData(String path) throws IOException;

    void clearHistoricalFiles();
}
