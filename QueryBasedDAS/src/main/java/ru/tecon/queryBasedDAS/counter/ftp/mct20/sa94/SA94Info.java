package ru.tecon.queryBasedDAS.counter.ftp.mct20.sa94;

import ru.tecon.queryBasedDAS.counter.ftp.mct20.FtpCounterInfo;

import java.util.Arrays;
import java.util.List;

/**
 * Информация по счетчику МСТ20-SA94
 *
 * @author Maksim Shchelkonogov
 * 13.02.2024
 */
public class SA94Info extends FtpCounterInfo {

    private static final String COUNTER_NAME = "МСТ-20-SA94";

    private static final List<String> PATTERNS = Arrays.asList("(\\d{4})s(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])", "(\\d{4})e(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])");

    public SA94Info() {
        super(PATTERNS);
    }

    @Override
    public String getCounterName() {
        return COUNTER_NAME;
    }
}
