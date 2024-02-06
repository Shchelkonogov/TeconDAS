package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.PropertiesLoader;
import ru.tecon.queryBasedDAS.UploadServiceEJBFactory;
import ru.tecon.uploaderService.ejb.das.ConfigRequestRemote;
import ru.tecon.uploaderService.ejb.das.ListenerServiceRemote;
import ru.tecon.uploaderService.ejb.das.ListenerType;
import ru.tecon.uploaderService.model.Listener;

import javax.ejb.*;
import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Maksim Shchelkonogov
 * 18.01.2024
 */
@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ListenerServicesStatelessBean {

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASSingletonBean dasSingletonBean;

    // remote ejb over http
//    @Inject
//    @ConfigProperty(name = "payara.instance.http.port")
//    private String port;

    /**
     * Регистрация слушателя запросов на конфигурацию на серверах загрузки данных
     *
     * @return список серверов, где успешно удалось зарегистрировать слушателя
     * @throws DasException в случае ошибки регистрации слушателя
     */
    public List<String> registerConfigRequestListener() throws DasException {
        List<String> result = new ArrayList<>();
        try {
            Properties appProperties = PropertiesLoader.loadProperties("app.properties");
            String[] serverNames = appProperties.getProperty("uploadServerNames").split(" ");

            for (String serverName: serverNames) {
                try {
                    registerConfigRequestListener(serverName);
                    result.add(serverName);
                } catch (DasException e) {
                    logger.warn("Error register listener for remote server {} {}", serverName, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new DasException("Error application parameters", e);
        }

        return result;
    }

    /**
     * Регистрация слушателя запросов на конфигурацию на сервере загрузки данных
     *
     * @param uploaderServerName имя сервера загрузки данных
     * @throws DasException в случае ошибки регистрации слушателя
     */
    public void registerConfigRequestListener(String uploaderServerName) throws DasException {
        try {
            Properties appProperties = PropertiesLoader.loadProperties("app.properties");
            String dasName = appProperties.getProperty("dasName");

            Properties jndiProperties = new Properties();

            // remote ejb over http
//            jndiProperties.setProperty(Context.INITIAL_CONTEXT_FACTORY, RemoteEJBContextFactory.FACTORY_CLASS);
//            jndiProperties.setProperty(Context.PROVIDER_URL, "http://" + InetAddress.getLocalHost().getHostName() + ":" + port + "/ejb-invoker");

            // remote ejb RMI-IIOP/CSIv2
            jndiProperties.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.enterprise.naming.SerialInitContextFactory");
            jndiProperties.setProperty(Context.URL_PKG_PREFIXES, "com.sun.enterprise.naming");
            jndiProperties.setProperty(Context.STATE_FACTORIES, "com.sun.corba.ee.impl.presentation.rmi.JNDIStateFactoryImpl");
            jndiProperties.setProperty("org.omg.CORBA.ORBInitialHost", InetAddress.getLocalHost().getHostName());
            jndiProperties.setProperty("org.omg.CORBA.ORBInitialPort", "3700");

            Listener listener = new Listener(dasName,
                    ListenerType.CONFIGURATION,
                    jndiProperties,
                    "java:global/queryBasedDAS/configRequestBean!" + ConfigRequestRemote.class.getName(),
                    dasSingletonBean.counterNameSet());

            ListenerServiceRemote listenerServiceRemote = UploadServiceEJBFactory.getListenerServiceRemote(uploaderServerName);

            if (!listenerServiceRemote.containsListener(dasName)) {
                listenerServiceRemote.addListener(listener);
            }
        } catch (IOException | NamingException e) {
            throw new DasException("remote register service " + uploaderServerName + " unavailable", e);
        }
    }

    /**
     * Отмена регистрации слушателя на конфигурацию на серверах загрузки данных
     *
     * @return список серверов, где регистрация успешно отменена
     * @throws DasException в случае ошибки отмены регистрации
     */
    public List<String> unregisterConfigRequestListener() throws DasException {
        List<String> result = new ArrayList<>();
        try {
            Properties appProperties = PropertiesLoader.loadProperties("app.properties");
            String[] serverNames = appProperties.getProperty("uploadServerNames").split(" ");

            for (String serverName: serverNames) {
                try {
                    unregisterConfigRequestListener(serverName);
                    result.add(serverName);
                } catch (DasException e) {
                    logger.warn("Error unregister listener for remote server {} ", serverName, e);
                }
            }
        } catch (IOException e) {
            throw new DasException("Error application parameters", e);
        }
        return result;
    }

    /**
     * Отмена регистрации слушателя на конфигурацию на сервере загрузки данных
     *
     * @param uploaderServerName имя сервера загрузки данных
     * @throws DasException в случае ошибки отмены регистрации
     */
    public void unregisterConfigRequestListener(String uploaderServerName) throws DasException {
        try {
            Properties appProperties = PropertiesLoader.loadProperties("app.properties");

            ListenerServiceRemote postgres = UploadServiceEJBFactory.getListenerServiceRemote(uploaderServerName);

            postgres.removeListener(appProperties.getProperty("dasName"));
        } catch (IOException | NamingException e) {
            throw new DasException("remote unregister service " + uploaderServerName + " unavailable", e);
        }
    }
}
