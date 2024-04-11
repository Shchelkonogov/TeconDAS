package ru.tecon.queryBasedDAS.counter.ftp.mct20.plain;

import ru.tecon.queryBasedDAS.counter.WebConsole;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.FtpCounterInfo;

import java.util.Collections;
import java.util.List;

/**
 * Информация по счетчику МСТ20
 *
 * @author Maksim Shchelkonogov
 * 15.02.2024
 */
public class PlainInfo extends FtpCounterInfo implements WebConsole {

    private static volatile PlainInfo instance;

    private static final String COUNTER_NAME = "МСТ-20";

    private static final List<String> PATTERN = Collections.singletonList("(\\d{4})a(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])");
    private static final List<String> DAY_FILES_PATTERN = Collections.singletonList("(\\d{4})a(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])");

    private PlainInfo() {
        super(PATTERN, DAY_FILES_PATTERN);
    }

    public static PlainInfo getInstance() {
        if (instance == null) {
            synchronized (PlainInfo.class) {
                if (instance == null) {
                    instance = new PlainInfo();
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
    public String getConsoleUrl() {
        return "/mct/plain";
    }
}
