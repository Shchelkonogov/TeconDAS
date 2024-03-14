package ru.tecon.queryBasedDAS.counter.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Maksim Shchelkonogov
 * 12.03.2024
 */
public abstract class FtpCounter implements Counter, FtpCounterExtension {

    protected final Map<String, CounterData> counterData = new HashMap<>();

    protected void parseResults(List<DataModel> params, LocalDateTime startTime) {
        for (DataModel model: params) {
            if (model.getStartDateTime() == null || startTime.isAfter(model.getStartDateTime())) {
                CounterData counterDataItem = counterData.get(model.getParamName());
                if ((counterDataItem != null) && (counterDataItem.getValue() != null)) {
                    model.addData(counterDataItem.getValue(), startTime, counterDataItem.getQuality());
                }
            }
        }
    }

    protected InputStream checkFileExistAtFtp(FTPClient ftpClient, String path) throws IOException, DasException {
        InputStream inputStream = ftpClient.retrieveFileStream(path);

        int replyCode = ftpClient.getReplyCode();
        if (replyCode == 550) {
            throw new DasException("ftp file unavailable");
        }
        return inputStream;
    }

    /**
     * Метод возвращает список файлов для загрузки их информации в базу
     *
     * @param ftpClient клиент подключенный к ftp МСТ20
     * @param directoryPath путь к папке
     * @param date дата начала
     * @param pattern Паттерн поиска файлов. Обязательно требуется группа date, что бы найти дату
     * @param dateFormat формат для разбора даты в имени файла
     * @return список файлов
     */
    protected List<FileData> getFilesForLoad(FTPClient ftpClient, String directoryPath, LocalDateTime date, List<String> pattern,
                                             String dateFormat) throws IOException {
        List<FileData> fileData = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
        FTPFile[] ftpFiles = ftpClient.listFiles(directoryPath);

        for (String p: pattern) {
            Pattern compile = Pattern.compile(p);

            for (FTPFile ftpFile: ftpFiles) {
                Matcher matcher = compile.matcher(ftpFile.getName());
                if (matcher.matches()) {
                    try {
                        LocalDateTime dateTimeTemp = LocalDateTime.parse(matcher.group("date"), formatter);
                        if (date != null) {
                            if (dateTimeTemp.isAfter(date)) {
                                fileData.add(new FileData("/" + ftpFile.getName(), dateTimeTemp));
                            }
                        } else {
                            fileData.add(new FileData("/" + ftpFile.getName(), dateTimeTemp));
                        }
                    } catch (DateTimeParseException ignore) {
                    }
                }
            }
        }

        Collections.sort(fileData);

        return fileData;
    }
}
