package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.UploadServiceEJBFactory;
import ru.tecon.queryBasedDAS.counter.Counter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
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

    @EJB
    private ListenerServicesStatelessBean listenerBean;

    private static final List<String> COUNTERS = List.of(
            "ru.tecon.queryBasedDAS.counter.asDTS.ASDTSCounter"
    );

    private static final Map<String, String> COUNTERS_MAP = new HashMap<>();

    @PostConstruct
    private void init() {
        // Проверка правильности файла с параметрами
        UploadServiceEJBFactory.checkProps();

        // Загружаем данные по счетчикам
        for (String counter: COUNTERS) {
            try {
                Counter instance = (Counter) Class.forName(counter).getDeclaredConstructor().newInstance();
                COUNTERS_MAP.put(instance.getCounterInfo().getCounterName(), counter);
            } catch (ReflectiveOperationException e) {
                logger.warn("error load counter {}", counter, e);
            }
        }

        try {
            listenerBean.registerConfigRequestListener();
        } catch (DasException e) {
            logger.warn("Error register listener ", e);
        }
    }

    @PreDestroy
    private void destroy() {
        try {
            listenerBean.unregisterConfigRequestListener();
        } catch (DasException e) {
            logger.warn("Error unregister listener ", e);
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
}
