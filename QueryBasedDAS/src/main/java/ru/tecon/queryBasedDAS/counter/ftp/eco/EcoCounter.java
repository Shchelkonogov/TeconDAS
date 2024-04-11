package ru.tecon.queryBasedDAS.counter.ftp.eco;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.ftp.FtpClient;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounter;
import ru.tecon.queryBasedDAS.counter.ftp.model.CounterData;
import ru.tecon.queryBasedDAS.counter.ftp.model.FileData;
import ru.tecon.uploaderService.model.DataModel;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 01.03.2024
 */
public class EcoCounter extends FtpCounter {

    private static final Logger logger = LoggerFactory.getLogger(EcoCounter.class);

    private static final EcoInfo info = new EcoInfo();

    private final Map<String, List<String>> averageData = new HashMap<>();
    private final Map<String, List<String>> summaryData = new HashMap<>();

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<String> getConfig(String object) {
        Set<String> result = new HashSet<>();

        FtpClient ftpClient = new EcoFtpClient();

        String stationNumber = object.replace("Станция ", "");

        try {
            ftpClient.open();

            FTPClient connection = ftpClient.getConnection();

            FTPFile[] ftpFiles = connection.listFiles("/");

            FTPFile fileForParsConfig = Arrays.stream(ftpFiles)
                    .filter(ftpFile -> ftpFile.getName().matches("\\[measure_529]_\\[" + stationNumber + "]_\\[(20\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])-([0-5]0)].xml"))
                    .max(Comparator.comparing(ftpFile -> LocalDateTime.ofInstant(ftpFile.getTimestampInstant(), ftpFile.getTimestamp().getTimeZone().toZoneId())))
                    .orElseThrow(() -> new DasException("error filtering"));

            try (InputStream inputStream = checkFileExistAtFtp(ftpClient.getConnection(), "/" + fileForParsConfig.getName())) {
                XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
                XMLEventReader reader = xmlInputFactory.createXMLEventReader(inputStream);

                String pipe = "";
                String boiler = "";
                String param = "";
                String paramType = "";

                while (reader.hasNext()) {
                    XMLEvent xmlEvent = reader.nextEvent();
                    if (xmlEvent.isStartElement()) {
                        StartElement startElement = xmlEvent.asStartElement();
                        switch (startElement.getName().getLocalPart()) {
                            case "ID_SOURCE":
                                xmlEvent = reader.nextEvent();
                                pipe = "Труба " + xmlEvent.asCharacters().getData();
                                break;
                            case "ID_CHIMNEY":
                                xmlEvent = reader.nextEvent();
                                boiler = ":Котел " + xmlEvent.asCharacters().getData();
                                break;
                            case "ID_PARAM":
                                xmlEvent = reader.nextEvent();
                                param = ":Параметр " + xmlEvent.asCharacters().getData();
                                break;
                            case "VAL_AVG":
                                paramType = ":Среднее";
                                break;
                            case "VAL_MIN":
                                paramType = ":Минимальное";
                                break;
                            case "VAL_MAX":
                                paramType = ":Максимальное";
                                break;
                            case "DT_MIN":
                                paramType = ":Время минимального";
                                break;
                            case "DT_MAX":
                                paramType = ":Время максимального";
                                break;
                        }
                    }
                    if (xmlEvent.isEndElement()) {
                        EndElement endElement = xmlEvent.asEndElement();
                        switch (endElement.getName().getLocalPart()) {
                            case "VAL_AVG":
                                result.add("Суммарные:" + pipe + param + paramType);
                                result.add("Суммарные" + param + paramType);
                            case "VAL_MIN":
                            case "VAL_MAX":
                            case "DT_MIN":
                            case "DT_MAX":
                                result.add(pipe + boiler + param + paramType);
                                break;
                        }
                    }
                }
            } catch (DasException e) {
                logger.warn("ftp error", e);
                ftpClient.close();
                return result;
            } finally {
                if (connection.isConnected()) {
                    connection.completePendingCommand();
                }
            }

            ftpClient.close();
        } catch (DasException | IOException | XMLStreamException e) {
            logger.warn("Error load config", e);
        }

        return result;
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        logger.info("start load data from ftpCounter for {}", objectName);

        if (params.isEmpty()) {
            logger.info("finish load data from ftpCounter for {} because model is empty", objectName);
            return;
        }

        Collections.sort(params);

        String stationNumber = objectName.replace("Станция ", "");
        LocalDateTime date = params.get(0).getStartDateTime() == null ? null : params.get(0).getStartDateTime().plusHours(3);

        FtpClient ftpClient = new EcoFtpClient();
        try {
            ftpClient.open();

            // Составление списка файлов для чтения
            List<FileData> fileData = getFilesForLoad(ftpClient.getConnection(),
                    "/",
                    date,
                    Collections.singletonList("\\[measure_529]_\\[" + stationNumber + "]_\\[(?<date>(20\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])-([0-5]0))].xml"),
                    "yyyy-MM-dd_HH-mm");

            logger.info("station {} read {} files", objectName, fileData.size());

            // Разбор файлов
            for (FileData fData: fileData) {
                counterData.clear();
                averageData.clear();
                summaryData.clear();

                try {
                    readFile(checkFileExistAtFtp(ftpClient.getConnection(), fData.getPath()));
                } catch (DasException e) {
                    logger.warn("read file {} error {}", fData.getPath(), e.getMessage());
                    ftpClient.close();
                    return;
                } catch (XMLStreamException e) {
                    logger.warn("Error load data", e);
                } finally {
                    if (ftpClient.getConnection().isConnected()) {
                        ftpClient.getConnection().completePendingCommand();
                    }
                }

                // Подсчитываем средние значения
                calcAverageData();
                // Подсчитываем суммарные значения
                calcSummaryData();

                parseResults(params, fData.getDateTime().minusHours(3));
            }

            ftpClient.close();
        } catch (IOException e) {
            logger.warn("error load files list from ftp {}", objectName, e);
        }

        params.removeIf(dataModel -> dataModel.getData().isEmpty());

        logger.info("finish load data from ftpCounter for {}", objectName);
    }

    private <K, V> void addAggregateData(Map<K, List<V>> data, K key, V value) {
        if (data.containsKey(key)) {
            data.get(key).add(value);
        } else {
            data.put(key, new ArrayList<>(Collections.singletonList(value)));
        }
    }

    private void calcAverageData() {
        averageData.forEach((s, list) -> {
            double average = list.stream()
                    .filter(value -> {
                        try {
                            Double.parseDouble(value);
                            return true;
                        } catch (NumberFormatException ignore) {
                            return false;
                        }
                    })
                    .collect(Collectors.summarizingDouble(Double::parseDouble))
                    .getAverage();
            counterData.put(s, new CounterData(String.format(Locale.US, "%.4f", average)));
        });
    }

    private void calcSummaryData() {
        summaryData.forEach((s, list) -> {
            double sum = list.stream()
                    .filter(value -> {
                        try {
                            Double.parseDouble(value);
                            return true;
                        } catch (NumberFormatException ignore) {
                            return false;
                        }
                    })
                    .collect(Collectors.summarizingDouble(Double::parseDouble))
                    .getSum();
            counterData.put(s, new CounterData(String.format(Locale.US, "%.4f", sum)));
        });
    }

    private void readFile(InputStream in) throws IOException, XMLStreamException {
        try (BufferedInputStream inputStream = new BufferedInputStream(in)) {
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            XMLEventReader reader = xmlInputFactory.createXMLEventReader(inputStream);

            String pipe = "";
            String boiler = "";
            String param = "";
            String paramType = "";
            String value = "";

            while (reader.hasNext()) {
                XMLEvent xmlEvent = reader.nextEvent();
                if (xmlEvent.isStartElement()) {
                    StartElement startElement = xmlEvent.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
                        case "ID_SOURCE":
                            xmlEvent = reader.nextEvent();
                            pipe = "Труба " + xmlEvent.asCharacters().getData();
                            break;
                        case "ID_CHIMNEY":
                            xmlEvent = reader.nextEvent();
                            boiler = ":Котел " + xmlEvent.asCharacters().getData();
                            break;
                        case "ID_PARAM":
                            xmlEvent = reader.nextEvent();
                            param = ":Параметр " + xmlEvent.asCharacters().getData();
                            break;
                        case "VAL_AVG":
                            xmlEvent = reader.nextEvent();
                            if (xmlEvent.isCharacters()) {
                                value = xmlEvent.asCharacters().getData();
                            }
                            paramType = ":Среднее";
                            break;
                        case "VAL_MIN":
                            xmlEvent = reader.nextEvent();
                            if (xmlEvent.isCharacters()) {
                                value = xmlEvent.asCharacters().getData();
                            }
                            paramType = ":Минимальное";
                            break;
                        case "VAL_MAX":
                            xmlEvent = reader.nextEvent();
                            if (xmlEvent.isCharacters()) {
                                value = xmlEvent.asCharacters().getData();
                            }
                            paramType = ":Максимальное";
                            break;
                        case "DT_MIN":
                            xmlEvent = reader.nextEvent();
                            if (xmlEvent.isCharacters()) {
                                value = xmlEvent.asCharacters().getData();
                            }
                            paramType = ":Время минимального";
                            break;
                        case "DT_MAX":
                            xmlEvent = reader.nextEvent();
                            if (xmlEvent.isCharacters()) {
                                value = xmlEvent.asCharacters().getData();
                            }
                            paramType = ":Время максимального";
                            break;
                    }
                }
                if ((value != null) && !value.equals("NaN") && xmlEvent.isEndElement()) {
                    EndElement endElement = xmlEvent.asEndElement();
                    switch (endElement.getName().getLocalPart()) {
                        case "VAL_AVG":
                            if (param.matches("^:Параметр (0301|0304|0337|10006|10008)$")) {
                                addAggregateData(summaryData, "Суммарные:" + pipe + param + paramType, value);
                                addAggregateData(summaryData, "Суммарные" + param + paramType, value);
                            } else {
                                if (param.matches("^:Параметр (10005|10007|33701)$")) {
                                    addAggregateData(averageData, "Суммарные:" + pipe + param + paramType, value);
                                    addAggregateData(averageData, "Суммарные" + param + paramType, value);
                                }
                            }
                        case "VAL_MIN":
                        case "VAL_MAX":
                        case "DT_MIN":
                        case "DT_MAX":
                            counterData.put(pipe + boiler + param + paramType, new CounterData(value));
                            value = null;
                            break;
                    }
                }
            }
        }
    }

    @Override
    public void showData(String path) throws IOException {
        FtpClient ftpClient = new EcoFtpClient();
        ftpClient.open();

        try {
            readFile(checkFileExistAtFtp(ftpClient.getConnection(), path));
        } catch (DasException e) {
            logger.warn("read file {} error {}", path, e.getMessage());
            ftpClient.close();
            return;
        } catch (XMLStreamException e) {
            logger.warn("read file {} error {}", path, e.getMessage());
        } finally {
            if (ftpClient.getConnection().isConnected()) {
                ftpClient.getConnection().completePendingCommand();
            }
        }

        calcAverageData();
        calcSummaryData();

        System.out.println(path);
        System.out.println(this);

        ftpClient.close();
    }

    @Override
    public void clearHistoricalFiles() {
        try {
            FtpClient ftpClient = new EcoFtpClient();
            ftpClient.open();

            FTPFile[] ftpFiles = ftpClient.getConnection().listFiles("/");

            Set<String> filesForRemove = Arrays.stream(ftpFiles)
                    .filter(ftpFile -> {
                        if (ftpFile.getName().matches("\\[measure_529]_\\[\\d{4}]_\\[(20\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])-([0-5]0)].xml")) {
                            return LocalDateTime.ofInstant(ftpFile.getTimestampInstant(), ftpFile.getTimestamp().getTimeZone().toZoneId())
                                    .isBefore(LocalDateTime.now().minusDays(7));
                        }
                        return false;
                    })
                    .map(ftpFile -> "/" + ftpFile.getName())
                    .collect(Collectors.toSet());

            for (String path: filesForRemove) {
                logger.info("remove file {}", path);
                ftpClient.getConnection().deleteFile(path);
            }

            ftpClient.close();
        } catch (IOException e) {
            logger.warn("error remove historical files", e);
        }
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",\n", EcoCounter.class.getSimpleName() + "." + info.getCounterName() + "[\n", "\n]");
        for (Map.Entry<String, CounterData> entry: counterData.entrySet()) {
            joiner.add("   " + entry.getKey() + ": " + entry.getValue().getValue() + " (quality: " + entry.getValue().getQuality() + ")");
        }
        return joiner.toString();
    }
}
