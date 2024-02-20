package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.ejb.observers.TimerEvent;

import javax.ejb.*;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.util.List;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
@Startup
@Singleton
@LocalBean
public class QueryBasedDASTimersBean {

    @Inject
    private Logger logger;

    @Inject
    Event<TimerEvent> event;

    @EJB
    private QueryBasedDASStatelessBean bean;

    @EJB
    private ListenerServicesStatelessBean listenerBean;

    /**
     * Таймер для проверки регистрации слушателей
     *
     * @param timer информация о таймере
     */
    @Schedule(minute = "*/20", hour = "*", info = "00, 20, 40 minute every hour", persistent = false)
    private void registerListeners(Timer timer) {
        event.fire(new TimerEvent(timer.getInfo().toString()));
        try {
            listenerBean.registerConfigRequestListener();
        } catch (DasException e) {
            logger.warn("Error register listener ", e);
        }
    }

    /**
     * Таймер загрузки архивных данных по счетчикам
     *
     * @param timer информация о таймере
     */
    @Schedule(minute = "10/20", hour = "*", info = "at 10, 30, 50 minute every hour", persistent = false)
    private void readHistoricalDataFourTimes(Timer timer) {
        // TODO remove comment on product
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("Start read historical data");
        bean.loadHistoricalData(Periodicity.THREE_TIME_PER_HOUR);
    }

    /**
     * Таймер загрузки архивных данных по счетчикам
     *
     * @param timer информация о таймере
     */
    @Schedule(minute = "20", hour = "*", info = "at 20 minute every hour", persistent = false)
    private void readHistoricalDataTwoTimes(Timer timer) {
        // TODO remove comment on product
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("Start read historical data");
        bean.loadHistoricalData(Periodicity.ONE_TIME_PER_HOUR);
    }

    /**
     * Таймер загрузки новых объектов счетчиков
     *
     * @param timer информация о таймере
     */
    @Schedule(info = "at 00:00 every day", persistent = false)
    private void loadObjects(Timer timer) {
        // TODO выполняется около 20 минут, похоже надо делать асинхронно
        event.fire(new TimerEvent(timer.getInfo().toString()));
        List<String> successServersSend = bean.uploadCounterObjects();
        logger.info("Success upload counter objects to {}", successServersSend);
    }
}
