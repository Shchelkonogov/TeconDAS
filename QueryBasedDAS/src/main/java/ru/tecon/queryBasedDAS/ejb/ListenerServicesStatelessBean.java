package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterType;
import ru.tecon.uploaderService.ejb.das.ListenerServiceRemote;
import ru.tecon.uploaderService.ejb.das.ListenerType;
import ru.tecon.uploaderService.ejb.das.RemoteRequest;
import ru.tecon.uploaderService.model.AccessType;
import ru.tecon.uploaderService.model.Listener;

import javax.ejb.*;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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

    /**
     * Регистрация слушателя запросов на конфигурацию на серверах загрузки данных
     *
     * @return список серверов, где успешно удалось зарегистрировать слушателя
     */
    public List<String> registerConfigRequestListener() {
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

            Properties properties = new Properties();
            properties.put("jndiProperties", remoteEJBFactory.getRemoteServiceProperties(InetAddress.getLocalHost().getHostName(), 3700));
            properties.put("lookupName", "java:global/queryBasedDAS/configRequestBean!" + RemoteRequest.class.getName());

            Listener listener = new Listener(dasName,
                    ListenerType.CONFIGURATION,
                    properties,
                    AccessType.REMOTE_EJB,
                    dasSingletonBean.counterNameSet(uploaderServerName));

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
     */
    public List<String> unregisterConfigRequestListener() {
        List<String> result = new ArrayList<>();

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                unregisterConfigRequestListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error unregister listener for remote server {} {}", serverName, e.getMessage());
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
     */
    public List<String> registerAsyncRequestListener() {
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
        String dasName = dasSingletonBean.getDasName();

        Set<String> counters = dasSingletonBean.counterNameSet(uploaderServerName);
        Set<String> asyncRequestCounters = dasSingletonBean.counterSupportAsyncRequestNameSet();
        asyncRequestCounters.removeIf(counterName -> !counters.contains(counterName));

        if (!asyncRequestCounters.isEmpty()) {
            try {
                Properties properties = new Properties();
                properties.put("jndiProperties", remoteEJBFactory.getRemoteServiceProperties(InetAddress.getLocalHost().getHostName(), 3700));
                properties.put("lookupName", "java:global/queryBasedDAS/asyncRequestBean!" + RemoteRequest.class.getName());

                Listener listener = new Listener(dasName,
                        ListenerType.INSTANT_DATA,
                        properties,
                        AccessType.REMOTE_EJB,
                        asyncRequestCounters);

                ListenerServiceRemote listenerServiceRemote = remoteEJBFactory.getListenerServiceRemote(uploaderServerName);

                if (!listenerServiceRemote.containsListener(dasName, ListenerType.INSTANT_DATA)) {
                    listenerServiceRemote.addListener(listener);
                }
            } catch (IOException | NamingException e) {
                throw new DasException("remote register service " + uploaderServerName + " unavailable", e);
            }
        }
    }

    /**
     * Отмена регистрации слушателя на мгновенные данные на серверах загрузки данных
     *
     * @return список серверов, где регистрация успешно отменена
     */
    public List<String> unregisterAsyncRequestListener() {
        List<String> result = new ArrayList<>();

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                unregisterAsyncRequestListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error unregister listener for remote server {} {}", serverName, e.getMessage());
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

    /**
     * Регистрация слушателя запросов на подписку на серверах загрузки данных
     *
     * @return список серверов, где успешно удалось зарегистрировать слушателя
     */
    public List<String> registerSubscriptionListener() {
        List<String> result = new ArrayList<>();

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                registerSubscriptionListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error register listener for remote server {} {}", serverName, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Регистрация слушателя запросов на подписку на сервере загрузки данных
     *
     * @param uploaderServerName имя сервера загрузки данных
     * @throws DasException в случае ошибки регистрации слушателя
     */
    public void registerSubscriptionListener(String uploaderServerName) throws DasException {
        String dasName = dasSingletonBean.getDasName();

        Set<String> counters = dasSingletonBean.counterNameSet(uploaderServerName);
        counters.removeIf(counterName -> dasSingletonBean.getCounterInfo(counterName).getCounterType() != CounterType.SUBSCRIPTION);

        if (!counters.isEmpty()) {
            try {
                Properties properties = new Properties();
                properties.put("jndiProperties", remoteEJBFactory.getRemoteServiceProperties(InetAddress.getLocalHost().getHostName(), 3700));
                properties.put("lookupName", "java:global/queryBasedDAS/subscriptionRequestBean!" + RemoteRequest.class.getName());

                Listener listener = new Listener(dasName,
                        ListenerType.SUBSCRIPTION,
                        properties,
                        AccessType.REMOTE_EJB,
                        counters);

                ListenerServiceRemote listenerServiceRemote = remoteEJBFactory.getListenerServiceRemote(uploaderServerName);

                if (!listenerServiceRemote.containsListener(dasName, ListenerType.SUBSCRIPTION)) {
                    listenerServiceRemote.addListener(listener);
                }
            } catch (IOException | NamingException e) {
                throw new DasException("remote register service " + uploaderServerName + " unavailable", e);
            }
        }
    }

    /**
     * Отмена регистрации слушателя на подписку на серверах загрузки данных
     *
     * @return список серверов, где регистрация успешно отменена
     */
    public List<String> unregisterSubscriptionListener() {
        List<String> result = new ArrayList<>();

        for (String serverName: dasSingletonBean.getRemotes().keySet()) {
            try {
                unregisterSubscriptionListener(serverName);
                result.add(serverName);
            } catch (DasException e) {
                logger.warn("Error unregister listener for remote server {} {}", serverName, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Отмена регистрации слушателя на подписку на сервере загрузки данных
     *
     * @param uploaderServerName имя сервера загрузки данных
     * @throws DasException в случае ошибки отмены регистрации
     */
    public void unregisterSubscriptionListener(String uploaderServerName) throws DasException {
        try {
            ListenerServiceRemote postgres = remoteEJBFactory.getListenerServiceRemote(uploaderServerName);

            postgres.removeListener(dasSingletonBean.getDasName(), ListenerType.SUBSCRIPTION);
        } catch (NamingException e) {
            throw new DasException("remote unregister service " + uploaderServerName + " unavailable", e);
        }
    }
}
