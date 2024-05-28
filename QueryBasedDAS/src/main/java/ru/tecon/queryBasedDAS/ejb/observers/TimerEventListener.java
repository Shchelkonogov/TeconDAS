package ru.tecon.queryBasedDAS.ejb.observers;

import org.slf4j.Logger;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import java.time.LocalDateTime;

/**
 * @author Maksim Shchelkonogov
 * 10.01.2024
 */
@Named
public class TimerEventListener {

    @Inject
    private Logger logger;

    public void eventListener(@Observes @Priority(2) TimerEvent event) {
        logger.info("event = {} at {}", event.getEventInfo(), event.getTime());
    }
}
