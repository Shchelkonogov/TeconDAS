package ru.tecon.queryBasedDAS.counter.scada;

import org.jetbrains.annotations.NotNull;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;
import ru.tecon.queryBasedDAS.counter.statistic.WebConsole;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * @author Maksim Shchelkonogov
 * 07.11.2024
 */
public class ScadaCounterInfo implements CounterInfo, WebConsole {

    static final String SCHEME = "http";
    static final String HOST = "10.24.18.10";
    static final int PORT = 5000;

    private static volatile ScadaCounterInfo instance;

    private static final String COUNTER_NAME = "Scada";
    private static final String COUNTER_USER_NAME = "Скада-Текон";

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();

    private final ScadaBean bean;

    public ScadaCounterInfo() throws NamingException {
        InitialContext ctx = new InitialContext();
        bean = (ScadaBean) ctx.lookup("java:global/queryBasedDAS/scada");
    }

    public static ScadaCounterInfo getInstance() {
        if (instance == null) {
            synchronized (ScadaCounterInfo.class) {
                if (instance == null) {
                    try {
                        instance = new ScadaCounterInfo();
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
    public String getCounterUserName() {
        return COUNTER_USER_NAME;
    }

    @Override
    public List<String> getObjects() {
        return bean.getObjects(SCHEME, HOST, PORT);
    }

    @Override
    public String getConsoleUrl() {
        return "/scada";
    }

    @Override
    public void merge(StatKey key, @NotNull StatData value, @NotNull BiFunction<? super StatData, ? super StatData, ? extends StatData> remappingFunction) {
        statistic.merge(key, value, remappingFunction);
    }

    @Override
    public Map<StatKey, StatData> getStatistic() {
        return statistic;
    }

    public ScadaBean getBean() {
        return bean;
    }
}
