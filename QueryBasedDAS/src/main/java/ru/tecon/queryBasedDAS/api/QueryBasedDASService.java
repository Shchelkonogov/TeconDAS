package ru.tecon.queryBasedDAS.api;

import com.google.gson.Gson;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.ejb.ListenerServicesStatelessBean;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;
import ru.tecon.uploaderService.ejb.das.ListenerType;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * @author Maksim Shchelkonogov
 * 11.01.2024
 */
@Path("/")
public class QueryBasedDASService {

    @Inject
    private Logger logger;

    @Inject
    private Gson json;

    @EJB
    private QueryBasedDASSingletonBean dasSingletonBean;

    @EJB
    private ListenerServicesStatelessBean listenerServicesBean;

    /**
     * Echo запрос.
     *
     * @param original echo
     * @return echo
     */
    @GET
    @Path("/echo")
    public String echo(@QueryParam("q") String original) {
        return original;
    }

    /**
     * Запрос на регистрацию системы сбора данных на сервере загрузки данных.
     *
     * @param serverName имя сервера загрузки данных или null,
     *                   тогда будет регистрироваться на всех серверах,
     *                   прописанных в конфигурационном файле.
     * @return возвращает список серверов, на которых получилось зарегистрироваться
     */
    @GET
    @Path("/registerListeners")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN + "; charset=UTF-8"})
    public Response registerListeners(@QueryParam("server") String serverName) {
        Map<String, List<String>> result = new HashMap<>();
        if (serverName == null) {
            result.put(ListenerType.CONFIGURATION.toString(), listenerServicesBean.registerConfigRequestListener());

            result.put(ListenerType.INSTANT_DATA.toString(), listenerServicesBean.registerAsyncRequestListener());
        } else {
            try {
                listenerServicesBean.registerConfigRequestListener(serverName);
                result.put(ListenerType.CONFIGURATION.toString(), Collections.singletonList(serverName));
            } catch (DasException e) {
                logger.warn("Error register config listener", e);
            }

            try {
                listenerServicesBean.registerAsyncRequestListener(serverName);
                result.put(ListenerType.INSTANT_DATA.toString(), Collections.singletonList(serverName));
            } catch (DasException e) {
                logger.warn("Error register config listener", e);
            }
        }
        return Response.ok(json.toJson(result)).build();
    }

    /**
     * Запрос на аннуляцию регистрации системы сбора данных на сервере загрузки данных.
     *
     * @param serverName имя сервера загрузки данных или null,
     *                   тогда будет аннулирована регистрация на всех серверах,
     *                   прописанных в конфигурационном файле.
     * @return возвращает список серверов, на которых получилось аннулировать регистрацию
     */
    @GET
    @Path("/unregisterListeners")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN + "; charset=UTF-8"})
    public Response unregisterListeners(@QueryParam("server") String serverName) {
        Map<String, List<String>> result = new HashMap<>();
        if (serverName == null) {
            result.put(ListenerType.CONFIGURATION.toString(), listenerServicesBean.unregisterConfigRequestListener());

            result.put(ListenerType.INSTANT_DATA.toString(), listenerServicesBean.unregisterAsyncRequestListener());
        } else {
            try {
                listenerServicesBean.unregisterConfigRequestListener(serverName);
                result.put(ListenerType.CONFIGURATION.toString(), Collections.singletonList(serverName));
            } catch (DasException e) {
                logger.warn("Error register config listener", e);
            }

            try {
                listenerServicesBean.unregisterAsyncRequestListener(serverName);
                result.put(ListenerType.INSTANT_DATA.toString(), Collections.singletonList(serverName));
            } catch (DasException e) {
                logger.warn("Error register config listener", e);
            }
        }
        return Response.ok(json.toJson(result)).build();
    }

    /**
     * Получения списка серверов загрузки данных, прописанных в конфигурационном файле.
     *
     * @return список серверов загрузки данных.
     */
    @GET
    @Path("/getRemoteServers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServerNames() {
        return Response.ok(json.toJson(dasSingletonBean.getRemotes().keySet())).build();
    }

    /**
     * Получения списка счетчиков сбора данных в данной системе сбора данных
     *
     * @return список счетчиков сбора данных.
     */
    @GET
    @Path("/getCounterNames")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCounterNames() {
        Map<String, Set<String>> result = new HashMap<>();
        for (String remote: dasSingletonBean.getRemotes().keySet()) {
            result.put(remote, dasSingletonBean.counterNameSet(remote));
        }
        return Response.ok(json.toJson(result)).build();
    }
}
