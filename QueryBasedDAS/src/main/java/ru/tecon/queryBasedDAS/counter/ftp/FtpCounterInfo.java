package ru.tecon.queryBasedDAS.counter.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.Periodicity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Абстрактный класс для получения информации о счетчиках на основе ftp МСТ20
 *
 * @author Maksim Shchelkonogov
 * 06.02.2024
 */
public abstract class FtpCounterInfo implements CounterInfo {

    private static final Logger logger = LoggerFactory.getLogger(FtpCounterInfo.class);

    private final List<String> patterns;
    private List<String> dayFilePatterns = new ArrayList<>();

    public FtpCounterInfo(List<String> patterns) {
        this.patterns = patterns;
    }

    public FtpCounterInfo(List<String> patterns, List<String> dayFilePatterns) {
        this.patterns = patterns;
        this.dayFilePatterns = dayFilePatterns;
    }

    @Override
    public List<String> getObjects() {
        List<String> objects = new ArrayList<>();

        FilesFilter<String> filter = (ftpClient, path) -> {
            FTPFile[] ftpFiles = ftpClient.listFiles(path);
            boolean contains = Arrays.stream(ftpFiles)
                    .map(FTPFile::getName)
                    .anyMatch(ftpFileName -> {
                        for (String fReg: patterns) {
                            if (ftpFileName.matches(fReg)) {
                                return true;
                            }
                        }
                        return false;
                    });
            if (contains) {
                return path;
            }
            return null;
        };

        try {
            FtpClient ftpClient = new FtpClient();

            ftpClient.open();

            search(objects, ftpClient.getConnection(), "", "^\\d{2}$", filter);

            ftpClient.close();
        } catch (IOException e) {
            logger.warn("error scan folder", e);
        }

        return objects.stream().map(s -> getCounterName() + "-" + s.substring(s.lastIndexOf('/') + 1)).collect(Collectors.toList());
    }

    @Override
    public Periodicity getPeriodicity() {
        return Periodicity.THREE_TIME_PER_HOUR;
    }

    /**
     * Обход всех папок на ftp с применением поиска по {@link FtpCounterInfo.FilesFilter}
     * @param result список счетчиков
     * @param ftpClient клиент подключенный к ftp МСТ20
     * @param folder путь для поиска
     * @param regex паттерн поиска имен папок
     * @param filter паттерн для поиска файлов с определенным именем
     * @param <T> Тип возврата значений в список result
     */
    private <T> void search(List<T> result, FTPClient ftpClient, String folder, String regex, FilesFilter<T> filter) {
        try {
            FTPFile[] ftpFiles = ftpClient.listDirectories(folder);
            List<String> filteredFtpFiles = Arrays.stream(ftpFiles)
                    .map(FTPFile::getName)
                    .filter(ftpFileName -> ftpFileName.matches(regex))
                    .collect(Collectors.toList());

            for (String fileName: filteredFtpFiles) {
                switch (fileName.length()) {
                    case 2:
                        search(result, ftpClient, folder + "/" + fileName, "^\\d{4}$", filter);
                        break;
                    case 4:
                        logger.info("check folder {}", folder + "/" + fileName);
                        T value = filter.load(ftpClient, folder + "/" + fileName);
                        if (value != null) {
                            result.add(value);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            logger.warn("error scan folder", e);
        }
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public List<String> getDayFilePatterns() {
        return dayFilePatterns;
    }

    private interface FilesFilter<T> {
        T load(FTPClient ftpClient, String pathName) throws IOException;
    }
}
