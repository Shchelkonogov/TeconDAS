package ru.tecon.queryBasedDAS.counter.mfk;

import org.jetbrains.annotations.NotNull;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.CounterType;
import ru.tecon.queryBasedDAS.counter.mfk.ejb.MfkBean;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;
import ru.tecon.queryBasedDAS.counter.statistic.WebConsole;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * @author Maksim Shchelkonogov
 * 03.10.2024
 */
public class MfkInfo implements CounterInfo, WebConsole {

    private static volatile MfkInfo instance;

    private static final String COUNTER_NAME = "MFK";
    private static final String COUNTER_USER_NAME = "MFK-1500";
    private static final CounterType COUNTER_TYPE = CounterType.QUERY;

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();
    private List<String> locked = new ArrayList<>();

    private final MfkBean bean;

    public MfkInfo() throws NamingException {
        InitialContext ctx = new InitialContext();
        bean = (MfkBean) ctx.lookup("java:global/queryBasedDAS/mfk");
    }

    public static MfkInfo getInstance() {
        if (instance == null) {
            synchronized (MfkInfo.class) {
                if (instance == null) {
                    try {
                        instance = new MfkInfo();
                    } catch (NamingException e) {
                        throw new RuntimeException(e);
                    }
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
        return bean.getObjects();
    }

    @Override
    public String getConsoleUrl() {
        return "/mfk";
    }

    @Override
    public void merge(StatKey key, @NotNull StatData value, @NotNull BiFunction<? super StatData, ? super StatData, ? extends StatData> remappingFunction) {
        statistic.merge(key, value, remappingFunction);
    }

    @Override
    public Map<StatKey, StatData> getStatistic() {
        return statistic;
    }

    public MfkBean getBean() {
        return bean;
    }

    public List<String> getLocked() {
        return locked;
    }

    public void setLocked(List<String> locked) {
        this.locked = locked;
    }
}
