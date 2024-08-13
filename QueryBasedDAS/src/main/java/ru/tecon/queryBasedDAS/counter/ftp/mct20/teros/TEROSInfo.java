package ru.tecon.queryBasedDAS.counter.ftp.mct20.teros;

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
 * Информация по счетчику МСТ20-TEROS
 *
 * @author Maksim Shchelkonogov
 * 12.02.2024
 */
public class TEROSInfo extends FtpCounterInfo {

    private static volatile TEROSInfo instance;

    private static final String COUNTER_NAME = "МСТ-20-TEROS";

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();

    private static final List<String> PATTERN = Collections.singletonList("(\\d{4})t(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])");

    private TEROSInfo() {
        super(PATTERN);
    }

    public static TEROSInfo getInstance() {
        if (instance == null) {
            synchronized (TEROSInfo.class) {
                if (instance == null) {
                    instance = new TEROSInfo();
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
    public String getCounterUserName() {
        return COUNTER_NAME;
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
