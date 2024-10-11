package ru.tecon.queryBasedDAS.ejb;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterAlarm;
import ru.tecon.queryBasedDAS.counter.CounterAsyncRequest;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterExtension;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;
import ru.tecon.queryBasedDAS.counter.statistic.StatisticSerializer;
import ru.tecon.queryBasedDAS.counter.statistic.WebConsole;
import ru.tecon.queryBasedDAS.ejb.prop.AppProp;
import ru.tecon.queryBasedDAS.ejb.prop.RemoteProp;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
@Startup
@Singleton
@LocalBean
public class QueryBasedDASSingletonBean {

    private AppProp appProp;

    private static final Map<String, CounterProp> COUNTER_PROP_MAP = new HashMap<>();
    private static final Map<String, RemoteProp> REMOTE_PROP_MAP = new HashMap<>();

    @Inject
    private Logger logger;

    @Inject
    private Gson json;

    @PostConstruct
    private void init() {
        try (InputStream resourceAsStream = QueryBasedDASSingletonBean.class.getClassLoader().getResourceAsStream("das.json")) {
            if (resourceAsStream != null) {
                try (InputStreamReader inputStreamReader = new InputStreamReader(resourceAsStream)) {
                    appProp = json.fromJson(inputStreamReader, AppProp.class);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("error init project", e);
        }
        if (appProp != null) {
            Path root = Paths.get("").toAbsolutePath();
            Map<String, RemoteProp> customRemoteProp = new HashMap<>();
            Path path = Paths.get(root + "/" + appProp.getDasName() + "/remote.json");
            if (Files.exists(path)) {
                try (InputStream inputStream = Files.newInputStream(path);
                     InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
                    customRemoteProp = json.fromJson(inputStreamReader,
                            new TypeToken<Map<String, RemoteProp>>() {
                            }.getType());
                } catch (IOException ignore) {
                }
            }

            for (String remoteName: appProp.getRemotes().keySet()) {
                REMOTE_PROP_MAP.put(remoteName, new RemoteProp());
                if (customRemoteProp.containsKey(remoteName)) {
                    REMOTE_PROP_MAP.get(remoteName).setEnable(customRemoteProp.get(remoteName).isEnable());
                    REMOTE_PROP_MAP.get(remoteName).setEnableAlarm(customRemoteProp.get(remoteName).isEnableAlarm());
                }
            }

            for (String counter: appProp.getCounters()) {
                try {
                    Counter instance = (Counter) Class.forName(counter).getDeclaredConstructor().newInstance();
                    String counterName = instance.getCounterInfo().getCounterName();
                    COUNTER_PROP_MAP.put(counterName, new CounterProp(counter, instance.getCounterInfo()));

                    for (String remoteName: appProp.getRemotes().keySet()) {
                        if (customRemoteProp.containsKey(remoteName) &&
                                (customRemoteProp.get(remoteName).getCounterProp(counterName) != null)) {
                            REMOTE_PROP_MAP.get(remoteName).addCounter(counterName);
                            RemoteProp.CounterProp customProp = customRemoteProp.get(remoteName).getCounterProp(counterName);
                            RemoteProp.CounterProp prop = REMOTE_PROP_MAP.get(remoteName).getCounterProp(counterName);

                            prop.setPeriodicity(customProp.getPeriodicity());
                            prop.setConcurrencyDepth(customProp.getConcurrencyDepth());
                            prop.setConcurrencyAlarmDepth(customProp.getConcurrencyAlarmDepth());
                        }
                    }
                } catch (ReflectiveOperationException e) {
                    logger.warn("error load counter {}", counter, e);
                    throw new RuntimeException("error load counter " + counter, e);
                }
            }

            // Де сериализация статистики
            COUNTER_PROP_MAP.forEach((counter, counterProp) -> {
                Path serPath = Paths.get(root + "/" + getDasName() + "/" + counter + ".ser");
                if (Files.exists(serPath) && (counterProp.info instanceof WebConsole)) {
                    try {
                        Map<StatKey, StatData> deserialize = StatisticSerializer.deserialize(serPath);
                        deserialize.forEach((key, value) -> ((WebConsole) COUNTER_PROP_MAP.get(counter).info).merge(key, value, (old, young) -> young));
                    } catch (DasException e) {
                        logger.warn("deserialize error", e);
                    }
                }
            });
        } else {
            throw new RuntimeException("error init project");
        }
    }

    @PreDestroy
    private void destroy() {
        Path root = Paths.get("").toAbsolutePath();
        Path path = Paths.get(root + "/" + getDasName() + "/remote.json");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream outputStream = Files.newOutputStream(path);
                 OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                json.toJson(REMOTE_PROP_MAP, writer);
            }
        } catch (IOException e) {
            logger.warn("Error write property", e);
        }

        // Сериализация статистики
        COUNTER_PROP_MAP.forEach((key, value) -> {
            Path serPath = Paths.get(root + "/" + getDasName() + "/" + key + ".ser");
            if (value.info instanceof WebConsole) {
                try {
                    StatisticSerializer.serialize(((WebConsole) value.info).getStatistic(), serPath);
                } catch (DasException e) {
                    logger.warn("serialize error", e);
                }
            }
        });
    }

    /**
     * Получение имя класса, который обрабатывает заданный счетчик
     *
     * @param key имя счетчика
     * @return имя класса обработчика
     */
    public String getCounter(String key) {
        return COUNTER_PROP_MAP.get(key).className;
    }

    /**
     * Получение коллекции имен доступных счетчиков системы для удаленного сервера
     *
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterNameSet(String remote) {
        Set<String> result = new HashSet<>();
        if (REMOTE_PROP_MAP.containsKey(remote)) {
            result.addAll(REMOTE_PROP_MAP.get(remote).getCounters());
        }
        return result;
    }

    /**
     * Получение имени счетчика для отображения
     *
     * @param counterName имя счетчика
     * @return имя счетчика для отображения
     */
    public String getUserCounterName(String counterName) {
        return COUNTER_PROP_MAP.get(counterName).info.getCounterUserName();
    }

    /**
     * Получение коллекции имен доступных счетчиков системы
     *
     * @param remote      имя удаленного сервера загрузки
     * @param periodicity частота опроса исторических данных счетчика
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterNameSet(String remote, Periodicity periodicity) {
        Set<String> result = new HashSet<>();
        if (REMOTE_PROP_MAP.containsKey(remote)) {
            result.addAll(REMOTE_PROP_MAP.get(remote).getCounters(periodicity));
        }
        return result;
    }

    /**
     * Получение коллекции имен доступных счетчиков системы с поддержкой очистки истории
     *
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterSupportRemoveHistoryNameSet() {
        return counterIsSupportClass(FtpCounterExtension.class);
    }

    /**
     * Получение коллекции имен доступных счетчиков системы с поддержкой alarm
     *
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterSupportAlarmNameSet() {
        return counterIsSupportClass(FtpCounterAlarm.class);
    }

    /**
     * Получение коллекции имен доступных счетчиков системы с поддержкой функции получения мгновенных данных
     *
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterSupportAsyncRequestNameSet() {
        return counterIsSupportClass(CounterAsyncRequest.class);
    }

    /**
     * Получение коллекции имен доступных счетчиков системы с поддержкой определенной функции
     *
     * @param clazz интерфейс описывающий функцию
     * @return имена доступных счетчиков системы
     */
    private Set<String> counterIsSupportClass(Class<?> clazz) {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, CounterProp> counter: COUNTER_PROP_MAP.entrySet()) {
            try {
                if (clazz.isAssignableFrom(Class.forName(counter.getValue().className))) {
                    result.add(counter.getKey());
                }
            } catch (ClassNotFoundException e) {
                logger.warn("error load counter {}", counter, e);
            }
        }
        return result;
    }

    public Map<String, String> getAllConsole() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, CounterProp> entry: COUNTER_PROP_MAP.entrySet()) {
            if (entry.getValue().info instanceof WebConsole) {
                result.put(entry.getKey(), ((WebConsole) entry.getValue().info).getConsoleUrl());
            }
        }
        return result;
    }

    public Map<String, AppProp.Remote> getRemotes() {
        return appProp.getRemotes();
    }

    public AppProp.Remote getRemote(String remote) {
        return appProp.getRemotes().get(remote);
    }

    public RemoteProp getRemoteProp(String remote) {
        return REMOTE_PROP_MAP.get(remote);
    }

    public RemoteProp.CounterProp getCounterProp(String remote, String counterName) {
        return REMOTE_PROP_MAP.get(remote).getCounterProp(counterName);
    }

    public String getDasName() {
        return appProp.getDasName();
    }

    public WebConsole getCounterWebConsole(String counterName) {
        if (COUNTER_PROP_MAP.get(counterName).info instanceof WebConsole) {
            return (WebConsole) COUNTER_PROP_MAP.get(counterName).info;
        } else {
            return null;
        }
    }

    private static class CounterProp {

        private final String className;
        private final CounterInfo info;

        private CounterProp(String className, CounterInfo info) {
            this.className = className;
            this.info = info;
        }
    }
}
