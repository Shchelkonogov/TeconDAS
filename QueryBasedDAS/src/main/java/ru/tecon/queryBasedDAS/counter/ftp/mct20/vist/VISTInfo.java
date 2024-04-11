package ru.tecon.queryBasedDAS.counter.ftp.mct20.vist;

import ru.tecon.queryBasedDAS.counter.ftp.mct20.FtpCounterInfo;

import java.util.Arrays;
import java.util.List;

/**
 * Информация по счетчику МСТ20-VIST
 *
 * @author Maksim Shchelkonogov
 * 06.02.2024
 */
public class VISTInfo extends FtpCounterInfo {

    private static volatile VISTInfo instance;

    private static final String COUNTER_NAME = "МСТ-20-VIST";

    private static final List<String> PATTERNS = Arrays.asList("(\\d{4})v(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])", "(\\d{4})h(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])");

    private VISTInfo() {
        super(PATTERNS);
    }

    public static VISTInfo getInstance() {
        if (instance == null) {
            synchronized (VISTInfo.class) {
                if (instance == null) {
                    instance = new VISTInfo();
                }
            }
        }
        return instance;
    }

    @Override
    public String getCounterName() {
        return COUNTER_NAME;
    }
}
