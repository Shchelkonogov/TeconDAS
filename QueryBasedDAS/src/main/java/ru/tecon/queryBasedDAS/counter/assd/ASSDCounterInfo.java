package ru.tecon.queryBasedDAS.counter.assd;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.CounterType;
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
 * 20.12.2024
 */
public class ASSDCounterInfo implements CounterInfo, WebConsole {

    static final String SCHEME = "http";
    static final String HOST = "172.20.248.118";
    static final int PORT = 8001;
    static final String API_KEY = "GdrTM7Egn8lVcsDszZKSXdopp9Kk7JRbymNBPWWDLkM";
    static final String CLIENT_ID = "37bca726-5cdd-4d7b-b88a-a8347f26327c";
    static final CounterType COUNTER_TYPE = CounterType.SUBSCRIPTION;

    private static volatile ASSDCounterInfo instance;

    private static final String COUNTER_NAME = "ASSD";
    private static final String COUNTER_USER_NAME = "АССД";

    private final Map<StatKey, StatData> statistic = new ConcurrentHashMap<>();

    @Getter
    private final ASSDBeanLocal bean;

    public ASSDCounterInfo() throws NamingException {
        InitialContext ctx = new InitialContext();
        bean = (ASSDBeanLocal) ctx.lookup("java:global/queryBasedDAS/assd");
    }

    public static ASSDCounterInfo getInstance() {
        if (instance == null) {
            synchronized (ASSDCounterInfo.class) {
                if (instance == null) {
                    try {
                        instance = new ASSDCounterInfo();
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
        return "/assd";
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
