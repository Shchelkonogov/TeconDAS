package ru.tecon.queryBasedDAS.counter.ftp.slave;

import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterInfo;

import java.util.Collections;
import java.util.List;

/**
 * Информация по счетчику МСТ20-SLAVE
 *
 * @author Maksim Shchelkonogov
 * 14.02.2024
 */
public class SLAVEInfo extends FtpCounterInfo {

    private static final String COUNTER_NAME = "МСТ-20-SLAVE";

    private static final List<String> PATTERN = Collections.singletonList("(\\d{4})b(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])");

    public SLAVEInfo() {
        super(PATTERN);
    }

    @Override
    public String getCounterName() {
        return COUNTER_NAME;
    }
}
