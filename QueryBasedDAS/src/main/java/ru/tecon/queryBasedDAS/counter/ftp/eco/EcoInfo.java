package ru.tecon.queryBasedDAS.counter.ftp.eco;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.CounterType;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;
import ru.tecon.queryBasedDAS.counter.statistic.WebConsole;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 01.03.2024
 */
public class EcoInfo implements CounterInfo, WebConsole {

    private static volatile EcoInfo instance;

    private static final Logger logger = LoggerFactory.getLogger(EcoInfo.class);

    private static final String COUNTER_NAME = "Ecomonitoring";
    private static final String COUNTER_USER_NAME = "Экомониторинг";
    private static final CounterType COUNTER_TYPE = CounterType.QUERY;

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();

    private EcoInfo() {
    }

    public static EcoInfo getInstance() {
        if (instance == null) {
            synchronized (EcoInfo.class) {
                if (instance == null) {
                    instance = new EcoInfo();
                }
            }
        }
        return instance;
    }

    @Override
    public String getCounterName() {
        return COUNTER_NAME;
    }

    @Override
    public CounterType getCounterType() {
        return COUNTER_TYPE;
    }

    @Override
    public String getCounterUserName() {
        return COUNTER_USER_NAME;
    }

    @Override
    public List<String> getObjects() {
        List<String> result = new ArrayList<>();

        EcoFtpClient ftpClient = new EcoFtpClient();

        try {
            ftpClient.open();

            FTPClient connection = ftpClient.getConnection();

            FTPFile[] ftpFiles = connection.listFiles("/");

            result = Arrays.stream(ftpFiles)
                    .filter(ftpFile -> ftpFile.getName().matches("\\[measure_529]_\\[\\d{4}]_\\[(20\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])-([0-5]0)].xml"))
                    .map(ftpFile -> "Станция " + ftpFile.getName().substring(15, 19))
                    .distinct()
                    .collect(Collectors.toList());

            ftpClient.close();
        } catch (IOException e) {
            logger.warn("Error load objects", e);
        }

        return result;
    }

    @Override
    public String getConsoleUrl() {
        return "/eco";
    }

    @Override
    public void merge(StatKey key, @NotNull StatData value, @NotNull BiFunction<? super StatData, ? super StatData, ? extends StatData> remappingFunction) {
        statistic.merge(key, value, remappingFunction);
    }

    @Override
    public Map<StatKey, StatData> getStatistic() {
        return statistic;
    }
}
