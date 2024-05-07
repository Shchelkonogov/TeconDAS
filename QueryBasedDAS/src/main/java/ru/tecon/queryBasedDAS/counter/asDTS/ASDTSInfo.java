package ru.tecon.queryBasedDAS.counter.asDTS;

import org.jetbrains.annotations.NotNull;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;
import ru.tecon.queryBasedDAS.counter.statistic.WebConsole;
import ru.tecon.queryBasedDAS.counter.asDTS.ejb.ASDTSMsSqlBean;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
public class ASDTSInfo implements CounterInfo, WebConsole {

    private static volatile ASDTSInfo instance;

    private static final String COUNTER_NAME = "IASDTU";

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();

    private final ASDTSMsSqlBean bean;

    private ASDTSInfo() throws NamingException {
        InitialContext ctx = new InitialContext();
        bean = (ASDTSMsSqlBean) ctx.lookup("java:global/queryBasedDAS/ejb/asDTSMsSql");
    }

    public static ASDTSInfo getInstance() {
        if (instance == null) {
            synchronized (ASDTSInfo.class) {
                if (instance == null) {
                    try {
                        instance = new ASDTSInfo();
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
    public List<String> getObjects() {
        return bean.getObjects();
    }

    public ASDTSMsSqlBean getBean() {
        return bean;
    }

    @Override
    public String getConsoleUrl() {
        return "/asdts";
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
