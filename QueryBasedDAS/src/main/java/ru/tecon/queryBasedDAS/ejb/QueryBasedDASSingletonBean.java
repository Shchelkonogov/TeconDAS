package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterAlarm;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterExtension;

import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
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

    private static final List<String> COUNTERS = List.of(
            "ru.tecon.queryBasedDAS.counter.asDTS.ASDTSCounter",
            "ru.tecon.queryBasedDAS.counter.ftp.vist.VISTCounter",
            "ru.tecon.queryBasedDAS.counter.ftp.teros.TEROSCounter",
            "ru.tecon.queryBasedDAS.counter.ftp.sa94.SA94Counter",
            "ru.tecon.queryBasedDAS.counter.ftp.slave.SLAVECounter",
            "ru.tecon.queryBasedDAS.counter.ftp.plain.PlainCounter"
    );

    private static final Map<String, String> COUNTERS_MAP = new HashMap<>();

    @PostConstruct
    private void init() {
        // Загружаем данные по счетчикам
        for (String counter: COUNTERS) {
            try {
                Counter instance = (Counter) Class.forName(counter).getDeclaredConstructor().newInstance();
                COUNTERS_MAP.put(instance.getCounterInfo().getCounterName(), counter);
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
        for (Map.Entry<String, String> counter: COUNTERS_MAP.entrySet()) {
            try {
                Counter instance = (Counter) Class.forName(counter.getValue()).getDeclaredConstructor().newInstance();
                if (instance.getCounterInfo().getPeriodicity() == periodicity) {
                    result.add(counter.getKey());
                }
            } catch (ReflectiveOperationException e) {
                logger.warn("error load counter {}", counter, e);
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
        Set<String> result = new HashSet<>();

        for (Map.Entry<String, String> counter: COUNTERS_MAP.entrySet()) {
            try {
                if (FtpCounterExtension.class.isAssignableFrom(Class.forName(counter.getValue()))) {
                    result.add(counter.getKey());
                }
            } catch (ClassNotFoundException e) {
                logger.warn("error load counter {}", counter, e);
            }
        }
        return result;
    }

    /**
     * Получение коллекции имен доступных счетчиков системы с поддержкой alarm
     *
     * @return имена доступных счетчиков системы
     */
    public Set<String> counterSupportAlarmNameSet() {
        Set<String> result = new HashSet<>();

        for (Map.Entry<String, String> counter: COUNTERS_MAP.entrySet()) {
            try {
                if (FtpCounterAlarm.class.isAssignableFrom(Class.forName(counter.getValue()))) {
                    result.add(counter.getKey());
                }
            } catch (ClassNotFoundException e) {
                logger.warn("error load counter {}", counter, e);
            }
        }
        return result;
    }
}
