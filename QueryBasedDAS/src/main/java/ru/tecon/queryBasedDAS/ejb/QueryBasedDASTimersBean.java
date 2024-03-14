package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.ejb.observers.TimerEvent;

import javax.ejb.*;
import javax.enterprise.event.Event;
import javax.inject.Inject;

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

        try {
            listenerBean.registerAsyncRequestListener();
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
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("Start read historical data");
        bean.loadHistoricalData(Periodicity.ONE_TIME_PER_HOUR);
    }

    /**
     * Таймер загрузки архивных данных по счетчикам
     *
     * @param timer информация о таймере
     */
    @Schedule(minute = "5/10", hour = "*", info = "at 5, 15, 25, 35, 45, 55 minute every hour", persistent = false)
    private void readHistoricalDataEveryTenMinutes(Timer timer) {
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("Start read historical data");
        bean.loadHistoricalData(Periodicity.EVERY_TEN_MINUTES);
    }

    /**
     * Таймер загрузки новых объектов счетчиков
     *
     * @param timer информация о таймере
     */
    @Schedule(info = "at 00:00 every day", persistent = false)
    private void loadObjects(Timer timer) {
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("start long time upload counter objects");
        bean.uploadCounterObjects();
    }

    /**
     * Таймер очистки исторических данных
     *
     * @param timer информация о таймере
     */
    @Schedule(hour = "1", info = "at 01:00 every day", persistent = false)
    private void clearObjects(Timer timer) {
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("start long time clear counter objects");
        bean.clearObjects();
    }

    /**
     * Таймер загрузки сообщений alarm
     *
     * @param timer информация о таймере
     */
    @Schedule(minute = "*/5", hour = "*", info = "every 5 minutes", persistent = false)
    private void readAlarms(Timer timer) {
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("start read alarms");
        bean.loadAlarms();
    }
}
