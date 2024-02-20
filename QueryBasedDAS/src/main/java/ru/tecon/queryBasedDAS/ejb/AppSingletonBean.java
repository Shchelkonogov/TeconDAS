package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.UploadServiceEJBFactory;

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
        // Проверка правильности файла с параметрами
        UploadServiceEJBFactory.checkProps();

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
}
