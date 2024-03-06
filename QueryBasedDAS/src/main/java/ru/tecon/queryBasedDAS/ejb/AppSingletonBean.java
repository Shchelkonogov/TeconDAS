package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

/**
 * @author Maksim Shchelkonogov
 * 07.02.2024
 */
@Startup
@Singleton
@LocalBean
public class AppSingletonBean {

    @Inject
    private Logger logger;

    @EJB
    private ListenerServicesStatelessBean listenerBean;

    @PostConstruct
    private void init() {
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

    @PreDestroy
    private void destroy() {
        try {
            listenerBean.unregisterConfigRequestListener();
        } catch (DasException e) {
            logger.warn("Error unregister listener ", e);
        }

        try {
            listenerBean.unregisterAsyncRequestListener();
        } catch (DasException e) {
            logger.warn("Error unregister listener ", e);
        }
    }
}
