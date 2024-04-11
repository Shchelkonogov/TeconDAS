package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.PropertiesLoader;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterAlarm;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterAsyncRequest;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterExtension;

import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
@Startup
@Singleton
@LocalBean
public class QueryBasedDASSingletonBean {

    @Inject
    private Logger logger;

    private Properties appProperties;

    private static final List<String> COUNTERS = List.of(
            "ru.tecon.queryBasedDAS.counter.asDTS.ASDTSCounter",
            "ru.tecon.queryBasedDAS.counter.ftp.mct20.vist.VISTCounter",
            "ru.tecon.queryBasedDAS.counter.ftp.mct20.teros.TEROSCounter",
            "ru.tecon.queryBasedDAS.counter.ftp.mct20.sa94.SA94Counter",
            "ru.tecon.queryBasedDAS.counter.ftp.mct20.slave.SLAVECounter",
            "ru.tecon.queryBasedDAS.counter.ftp.mct20.plain.PlainCounter",
            "ru.tecon.queryBasedDAS.counter.ftp.eco.EcoCounter"
    );

    private static final Map<String, String> COUNTERS_MAP = new HashMap<>();
    private static final Map<String, Properties> COUNTERS_PROP_MAP = new HashMap<>();

    @PostConstruct
    private void init() {
        // Проверка правильности файла с параметрами
        appProperties = checkProps();

        // Загружаем данные по счетчикам
        for (String counter: COUNTERS) {
            try {
                Counter instance = (Counter) Class.forName(counter).getDeclaredConstructor().newInstance();
                COUNTERS_MAP.put(instance.getCounterInfo().getCounterName(), counter);

                try {
                    Properties prop = PropertiesLoader.loadProperties(instance.getCounterInfo().getCounterName() + ".properties");
                    if (!prop.isEmpty()) {
                        COUNTERS_PROP_MAP.put(instance.getCounterInfo().getCounterName(), prop);
                    }
                } catch (IOException ignore) {
                }
            } catch (ReflectiveOperationException e) {
                logger.warn("error load counter {}", counter, e);
            }
        }
    }

    /**
     * Проверка, существует ли данный счетчик в системе
     *
     * @param key имя счетчика
     * @return true, если существует
     */
    public boolean containsCounter(String key) {
        return COUNTERS_MAP.containsKey(key);
    }

    /**
     * Получение имя класса, который обрабатывает заданный счетчик
     *
     * @param key имя счетчика
     * @return имя класса обработчика
     */
    public String getCounter(String key) {
        return COUNTERS_MAP.get(key);
    }

    /**
     * Получение коллекции имен доступных счетчиков системы
     *
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterNameSet() {
        return new HashSet<>(COUNTERS_MAP.keySet());
    }

    /**
     * Получение коллекции имен доступных счетчиков системы
     *
     * @param periodicity частота опроса исторических данных счетчика
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterNameSet(Periodicity periodicity) {
        Set<String> result = new HashSet<>();
        for (String counterName: COUNTERS_MAP.keySet()) {
            String period = getCounterProperty(counterName, "periodicity");
            period = period == null ? getProperty("periodicity") : period;
            if (Periodicity.valueOf(period) == periodicity) {
                result.add(counterName);
            }
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
        return counterIsSupportClass(FtpCounterAsyncRequest.class);
    }

    /**
     * Получение коллекции имен доступных счетчиков системы с поддержкой определенной функции
     *
     * @param clazz интерфейс описывающий функцию
     * @return имена доступных счетчиков системы
     */
    private Set<String> counterIsSupportClass(Class<?> clazz) {
        Set<String> result = new HashSet<>();

        for (Map.Entry<String, String> counter: COUNTERS_MAP.entrySet()) {
            try {
                if (clazz.isAssignableFrom(Class.forName(counter.getValue()))) {
                    result.add(counter.getKey());
                }
            } catch (ClassNotFoundException e) {
                logger.warn("error load counter {}", counter, e);
            }
        }
        return result;
    }

    /**
     * Проверка корректности ввода свойств
     */
    private Properties checkProps() {
        try {
            Properties appProperties = PropertiesLoader.loadProperties("app.properties");

            String[] uploadServerNames = appProperties.getProperty("uploadServerNames").split(" ");
            String[] uploadServerURIs = appProperties.getProperty("uploadServerURIs").split(" ");
            String[] uploadServerPorts = appProperties.getProperty("uploadServerPorts").split(" ");
            String[] uploaderServiceName = appProperties.getProperty("uploaderServiceName").split(" ");
            String[] uploaderEJBName = appProperties.getProperty("uploaderEJBName").split(" ");
            String[] listenerServiceEJBName = appProperties.getProperty("listenerServiceEJBName").split(" ");

            int remoteCount = uploadServerNames.length;

            // проверка записи параметров
            if ((remoteCount != uploadServerURIs.length) ||
                    (remoteCount != uploadServerPorts.length) ||
                    (remoteCount != uploaderServiceName.length) ||
                    (remoteCount != uploaderEJBName.length) ||
                    (remoteCount != listenerServiceEJBName.length)) {
                throw new RuntimeException("Error application parameters");
            }

            return appProperties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getProperty(String key) {
        return appProperties.getProperty(key);
    }

    public String getCounterProperty(String counterName, String key) {
        if (COUNTERS_PROP_MAP.containsKey(counterName)) {
            return COUNTERS_PROP_MAP.get(counterName).getProperty(key);
        } else {
            return null;
        }
    }
}
