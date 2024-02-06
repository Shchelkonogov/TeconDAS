package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
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
    // TODO remove comment on product
//    @Schedule(minute = "10/15", hour = "*", info = "10, 25, 40, 55 minute every hour", persistent = false)
    private void readHistoricalData(Timer timer) {
        event.fire(new TimerEvent(timer.getInfo().toString()));
        logger.info("Start read historical data");
        bean.loadHistoricalData();
    }

    /**
     * Таймер загрузки новых объектов счетчиков
     *
     * @param timer информация о таймере
     */
    @Schedule(info = "at 00:00 every day", persistent = false)
    private void loadObjects(Timer timer) {
        event.fire(new TimerEvent(timer.getInfo().toString()));
        List<String> successServersSend = bean.uploadCounterObjects();
        logger.info("Success upload counter objects to {}", successServersSend);
    }
}
