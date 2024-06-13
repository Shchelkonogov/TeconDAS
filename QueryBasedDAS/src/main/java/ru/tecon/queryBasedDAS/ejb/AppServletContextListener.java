package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * @author Maksim Shchelkonogov
 * 11.06.2024
 */
@WebListener
public class AppServletContextListener implements ServletContextListener {

    @Inject
    private Logger logger;

    @EJB
    private ListenerServicesStatelessBean listenerBean;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("init listeners");

        listenerBean.registerConfigRequestListener();

        listenerBean.registerAsyncRequestListener();

        ServletContextListener.super.contextInitialized(sce);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("destroy listeners");

        listenerBean.unregisterConfigRequestListener();

        listenerBean.unregisterAsyncRequestListener();

        ServletContextListener.super.contextDestroyed(sce);
    }
}
