package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.uploaderService.ejb.das.ConfigRequestRemote;
import ru.tecon.uploaderService.ejb.das.InstantDataRequestRemote;
import ru.tecon.uploaderService.ejb.das.ListenerServiceRemote;
import ru.tecon.uploaderService.ejb.das.ListenerType;
import ru.tecon.uploaderService.model.Listener;

import javax.ejb.*;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

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

    @EJB
    private RemoteEJBFactory remoteEJBFactory;

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

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                registerConfigRequestListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error register listener for remote server {} {}", serverName, e.getMessage());
            }
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
            String dasName = dasSingletonBean.getDasName();

            Listener listener = new Listener(dasName,
                    ListenerType.CONFIGURATION,
                    remoteEJBFactory.getRemoteServiceProperties(InetAddress.getLocalHost().getHostName(), 3700),
                    "java:global/queryBasedDAS/configRequestBean!" + ConfigRequestRemote.class.getName(),
                    dasSingletonBean.counterNameSet());

            ListenerServiceRemote listenerServiceRemote = remoteEJBFactory.getListenerServiceRemote(uploaderServerName);

            if (!listenerServiceRemote.containsListener(dasName, ListenerType.CONFIGURATION)) {
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

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                unregisterConfigRequestListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error unregister listener for remote server {} ", serverName, e);
            }
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
            ListenerServiceRemote postgres = remoteEJBFactory.getListenerServiceRemote(uploaderServerName);

            postgres.removeListener(dasSingletonBean.getDasName(), ListenerType.CONFIGURATION);
        } catch (NamingException e) {
            throw new DasException("remote unregister service " + uploaderServerName + " unavailable", e);
        }
    }

    /**
     * Регистрация слушателя запросов на конфигурацию на серверах загрузки данных
     *
     * @return список серверов, где успешно удалось зарегистрировать слушателя
     * @throws DasException в случае ошибки регистрации слушателя
     */
    public List<String> registerAsyncRequestListener() throws DasException {
        List<String> result = new ArrayList<>();

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                registerAsyncRequestListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error register listener for remote server {} {}", serverName, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Регистрация слушателя запросов на конфигурацию на сервере загрузки данных
     *
     * @param uploaderServerName имя сервера загрузки данных
     * @throws DasException в случае ошибки регистрации слушателя
     */
    public void registerAsyncRequestListener(String uploaderServerName) throws DasException {
        try {
            String dasName = dasSingletonBean.getDasName();

            Listener listener = new Listener(dasName,
                    ListenerType.INSTANT_DATA,
                    remoteEJBFactory.getRemoteServiceProperties(InetAddress.getLocalHost().getHostName(), 3700),
                    "java:global/queryBasedDAS/asyncRequestBean!" + InstantDataRequestRemote.class.getName(),
                    dasSingletonBean.counterSupportAsyncRequestNameSet());

            ListenerServiceRemote listenerServiceRemote = remoteEJBFactory.getListenerServiceRemote(uploaderServerName);

            if (!listenerServiceRemote.containsListener(dasName, ListenerType.INSTANT_DATA)) {
                listenerServiceRemote.addListener(listener);
            }
        } catch (IOException | NamingException e) {
            throw new DasException("remote register service " + uploaderServerName + " unavailable", e);
        }
    }

    /**
     * Отмена регистрации слушателя на мгновенные данные на серверах загрузки данных
     *
     * @return список серверов, где регистрация успешно отменена
     * @throws DasException в случае ошибки отмены регистрации
     */
    public List<String> unregisterAsyncRequestListener() throws DasException {
        List<String> result = new ArrayList<>();

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                unregisterAsyncRequestListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error unregister listener for remote server {} ", serverName, e);
            }
        }

        return result;
    }

    /**
     * Отмена регистрации слушателя на мгновенные данные на сервере загрузки данных
     *
     * @param uploaderServerName имя сервера загрузки данных
     * @throws DasException в случае ошибки отмены регистрации
     */
    public void unregisterAsyncRequestListener(String uploaderServerName) throws DasException {
        try {
            ListenerServiceRemote postgres = remoteEJBFactory.getListenerServiceRemote(uploaderServerName);

            postgres.removeListener(dasSingletonBean.getDasName(), ListenerType.INSTANT_DATA);
        } catch (NamingException e) {
            throw new DasException("remote unregister service " + uploaderServerName + " unavailable", e);
        }
    }
}
