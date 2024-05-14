package ru.tecon.queryBasedDAS.counter.ftp.mct20.plain;

import org.jetbrains.annotations.NotNull;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.FtpCounterInfo;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Информация по счетчику МСТ20
 *
 * @author Maksim Shchelkonogov
 * 15.02.2024
 */
public class PlainInfo extends FtpCounterInfo {

    private static volatile PlainInfo instance;

    private static final String COUNTER_NAME = "МСТ-20";

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();

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
    public void clearStatistic() {
        statistic.clear();
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
