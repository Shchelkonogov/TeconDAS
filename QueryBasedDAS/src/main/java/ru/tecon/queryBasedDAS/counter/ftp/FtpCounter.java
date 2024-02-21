package ru.tecon.queryBasedDAS.counter.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.ftp.model.CounterData;
import ru.tecon.queryBasedDAS.counter.ftp.model.FileData;
import ru.tecon.uploaderService.model.DataModel;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Абстрактный класс для обработки счетчиков на основе ftp МСТ20
 *
 * @author Maksim Shchelkonogov
 * 07.02.2024
 */
public abstract class FtpCounter implements Counter, FtpCounterExtension {

    private static final Logger logger = LoggerFactory.getLogger(FtpCounter.class);

    protected final Map<String, CounterData> counterData = new HashMap<>();

    private final FtpCounterInfo info;

    private static final int[] table = {
            0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
            0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
            0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
            0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
            0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
            0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
            0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
            0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
            0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
            0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
            0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
            0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
            0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
            0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
            0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
            0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
            0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
            0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
            0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
            0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
            0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
            0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
            0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
            0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
            0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
            0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
            0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
            0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
            0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
            0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
            0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
            0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
    };

    public FtpCounter(FtpCounterInfo info) {
        this.info = info;
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        logger.info("start load data from ftpCounter for {}", objectName);

        Collections.sort(params);

        String counterNumber = objectName.substring(objectName.length() - 4);
        String filePath = "/" + counterNumber.substring(0, 2) + "/" + counterNumber;

        LocalDateTime date = params.get(0).getStartDateTime() == null ? null : params.get(0).getStartDateTime().minusHours(1);

        FtpClient ftpClient = new FtpClient();
        try {
            ftpClient.open();

            List<FileData> fileData = getFilesForLoad(ftpClient.getConnection(), filePath, date, info.getPatterns());

            for (FileData fData: fileData) {
                try {
                    try {
                        InputStream inputStream;

                        try {
                            inputStream = checkFileExistAtFtp(ftpClient.getConnection(), fData.getPath());
                        } catch (DasException e) {
                            logger.warn("read file {} error {}", fData.getPath(), e.getMessage());
                            ftpClient.close();
                            return;
                        }

                        readFile(inputStream, fData.getPath());
                    } finally {
                        if (ftpClient.getConnection().isConnected()) {
                            ftpClient.getConnection().completePendingCommand();
                        }
                    }

                    for (DataModel model: params) {
                        if (model.getStartDateTime() == null || fData.getDateTime().isAfter(model.getStartDateTime().minusHours(1))) {
                            CounterData counterDataItem = counterData.get(model.getParamName());
                            if ((counterDataItem != null) && (counterDataItem.getValue() != null)) {
                                model.addData(counterDataItem.getValue(), fData.getDateTime(), counterDataItem.getQuality());
                            }
                        }
                    }
                } catch (DasException e) {
                    logger.warn("error load data from ftpCounter for {} file path {} error message {}", objectName, fData.getPath(), e.getMessage());
                    try {
                        markFileError(ftpClient.getConnection(), fData.getPath());
                    } catch (IOException ex) {
                        logger.warn("error rename to corrupted file {}", fData.getPath());
                    }
                } catch (IOException ex) {
                    logger.warn("error load data from ftpCounter for {}", objectName, ex);
                    ftpClient.close();
                    return;
                }
            }

            ftpClient.close();
        } catch (IOException e) {
            logger.warn("error load files list from ftp {}", objectName, e);
            return;
        }

        logger.info("finish load data from ftpCounter for {}", objectName);
    }

    @Override
    public void showData(String path) throws IOException {
        FtpClient ftpClient = new FtpClient();
        ftpClient.open();

        try {
            readFile(checkFileExistAtFtp(ftpClient.getConnection(), path), path);
        } catch (DasException e) {
            logger.warn("read file {} error {}", path, e.getMessage());
            ftpClient.close();
            return;
        } finally {
            if (ftpClient.getConnection().isConnected()) {
                ftpClient.getConnection().completePendingCommand();
            }
        }

        System.out.println(path);
        System.out.println(this);

//        List<String> objects = info.getObjects();
//        System.out.println(objects);

//        List<FileData> filesForLoad = FtpUtils.getFilesForLoad(ftpClient.getConnection(), "/03/0374", LocalDateTime.of(2024, 2, 7, 12, 0, 0), info.getPatterns());
//        System.out.println(filesForLoad);

        ftpClient.close();
    }

    @Override
    public void clearHistoricalFiles() {
        logger.info("start clear historical file for {}", info.getCounterName());

        for (String counterObject: info.getObjects()) {
            String counterNumber = counterObject.substring(counterObject.length() - 4);
            String directoryPath = "/" + counterNumber.substring(0, 2) + "/" + counterNumber;

            List<String> patterns = new ArrayList<>(info.getPatterns());
            patterns.addAll(info.getPatterns().stream().map(s -> s + "-er").collect(Collectors.toSet()));
            patterns.addAll(info.getDayFilePatterns());

            try {
                FtpClient ftpClient = new FtpClient();
                ftpClient.open();

                FTPFile[] ftpFiles = ftpClient.getConnection().listFiles(directoryPath);

                Set<String> filesForRemove = Arrays.stream(ftpFiles)
                        .filter(ftpFile -> {
                            for (String pattern: patterns) {
                                if (ftpFile.getName().matches(pattern)) {
                                    return LocalDateTime.ofInstant(ftpFile.getTimestampInstant(), ftpFile.getTimestamp().getTimeZone().toZoneId())
                                            .isBefore(LocalDateTime.now().minusDays(45));
                                }
                            }
                            return false;
                        })
                        .map(ftpFile -> directoryPath + "/" + ftpFile.getName())
                        .collect(Collectors.toSet());

                for (String path: filesForRemove) {
                    logger.info("remove file {}", path);
                    ftpClient.getConnection().deleteFile(path);
                }

                ftpClient.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        logger.info("finish clear historical file for {}", info.getCounterName());
    }

    private InputStream checkFileExistAtFtp(FTPClient ftpClient, String path) throws IOException, DasException {
        InputStream inputStream = ftpClient.retrieveFileStream(path);

        int replyCode = ftpClient.getReplyCode();
        if (replyCode == 550) {
            throw new DasException("ftp file unavailable");
        }
        return inputStream;
    }

    /**
     * Алгоритм расчета crc-16/modbus
     * @param data массив байтов
     * @return crc-16/modbus
     */
    public Integer computeCrc16(byte[] data) {
        int crc = 0xffff;
        for (byte b : data) {
            crc = (crc >>> 8) ^ table[(crc ^ b) & 0xff];
        }

        return crc;
    }

    private void markFileError(FTPClient ftpClient, String path) throws IOException {
        FTPFile ftpFile = ftpClient.mdtmFile(path);

        if (LocalDateTime.ofInstant(ftpFile.getTimestampInstant(), ftpFile.getTimestamp().getTimeZone().toZoneId())
                .isBefore(LocalDateTime.now().minusHours(24))) {
            ftpClient.rename(path, path + "-er");
        }
    }

    /**
     * Метод возвращает список файлов для загрузки их информации в базу
     * @param ftpClient клиент подключенный к ftp МСТ20
     * @param filePath путь к папке
     * @param date дата начала
     * @param pattern паттерн поиска файлов
     * @return список файлов
     */
    private List<FileData> getFilesForLoad(FTPClient ftpClient, String filePath, LocalDateTime date,
                                                 List<String> pattern) throws IOException {
        return getFilesForLoad(ftpClient, filePath, date, pattern, "yyyyMMdd-HH");
    }

    /**
     * Метод возвращает список файлов для загрузки их информации в базу
     * @param ftpClient клиент подключенный к ftp МСТ20
     * @param directoryPath путь к папке
     * @param date дата начала
     * @param pattern паттерн поиска файлов
     * @param dateFormat формат для разбора даты в имени файла
     * @return список файлов
     */
    private List<FileData> getFilesForLoad(FTPClient ftpClient, String directoryPath, LocalDateTime date, List<String> pattern,
                                                 String dateFormat) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);

        FTPFile[] ftpFiles = ftpClient.listFiles(directoryPath);

        return Arrays.stream(ftpFiles)
                .map(FTPFile::getName)
                .filter(ftpFileName -> {
                    for (String p: pattern) {
                        if (ftpFileName.matches(p)) {
                            try {
                                LocalDateTime dateTimeTemp = LocalDateTime.parse(ftpFileName.substring(ftpFileName.length() - dateFormat.length()),
                                        formatter);
                                if (date != null) {
                                    return dateTimeTemp.isAfter(date);
                                } else {
                                    return true;
                                }
                            } catch (DateTimeParseException e) {
                                return false;
                            }
                        }
                    }
                    return false;
                })
                .map(ftpFileName -> {
                    LocalDateTime dateTimeTemp = LocalDateTime.parse(ftpFileName.substring(ftpFileName.length() - dateFormat.length()),
                            formatter);
                    return new FileData(directoryPath + "/" + ftpFileName, dateTimeTemp);
                }).sorted().collect(Collectors.toList());
    }

    public abstract void readFile(InputStream in, String path) throws IOException, DasException;

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",\n", FtpCounter.class.getSimpleName() + "." + info.getCounterName() + "[\n", "\n]");
        for (Map.Entry<String, CounterData> entry: counterData.entrySet()) {
            joiner.add("   " + entry.getKey() + ": " + entry.getValue().getValue() + " (quality: " + entry.getValue().getQuality() + ")");
        }
        return joiner.toString();
    }
}
