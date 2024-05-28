package ru.tecon.queryBasedDAS.counter.ftp.mct20.sa94;

import org.jetbrains.annotations.NotNull;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.FtpCounterInfo;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Информация по счетчику МСТ20-SA94
 *
 * @author Maksim Shchelkonogov
 * 13.02.2024
 */
public class SA94Info extends FtpCounterInfo {

    private static volatile SA94Info instance;

    private static final String COUNTER_NAME = "МСТ-20-SA94";

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();

    private static final List<String> PATTERNS = Arrays.asList("(\\d{4})s(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])", "(\\d{4})e(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])-([01][0-9]|2[0-3])");

    private SA94Info() {
        super(PATTERNS);
    }

    public static SA94Info getInstance() {
        if (instance == null) {
            synchronized (SA94Info.class) {
                if (instance == null) {
                    instance = new SA94Info();
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
    public void merge(StatKey key, @NotNull StatData value, @NotNull BiFunction<? super StatData, ? super StatData, ? extends StatData> remappingFunction) {
        statistic.merge(key, value, remappingFunction);
    }

    @Override
    public Map<StatKey, StatData> getStatistic() {
        return statistic;
    }
}
