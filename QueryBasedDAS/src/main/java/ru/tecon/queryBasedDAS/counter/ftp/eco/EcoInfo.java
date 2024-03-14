package ru.tecon.queryBasedDAS.counter.ftp.eco;

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
 * @author Maksim Shchelkonogov
 * 01.03.2024
 */
public class EcoInfo implements CounterInfo {

    private static final Logger logger = LoggerFactory.getLogger(EcoInfo.class);

    private static final String COUNTER_NAME = "Ecomonitoring";

    @Override
    public String getCounterName() {
        return COUNTER_NAME;
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
    public Periodicity getPeriodicity() {
        return Periodicity.EVERY_TEN_MINUTES;
    }
}
