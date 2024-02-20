package ru.tecon.queryBasedDAS.api;

import com.google.gson.Gson;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.PropertiesLoader;
import ru.tecon.queryBasedDAS.ejb.ListenerServicesStatelessBean;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASStatelessBean;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    private QueryBasedDASStatelessBean dasStatelessBean;

    @EJB
    private ListenerServicesStatelessBean listenerServicesBean;

//    // TODO remove method on product
//    @GET
//    @Path("/test")
//    public String test() {
//        return null;
//    }

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
     * @return возвращает список серверов, на которых получилось зарегистрироваться или ошибку 503 (service unavailable)
     */
    @GET
    @Path("/registerListeners")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN + "; charset=UTF-8"})
    public Response registerListeners(@QueryParam("server") String serverName) {
        if (serverName == null) {
            try {
                List<String> registeredServerNames = listenerServicesBean.registerConfigRequestListener();

                return Response.ok(json.toJson(registeredServerNames)).build();
            } catch (DasException e) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Remote service unavailable").build();
            }
        } else {
            try {
                listenerServicesBean.registerConfigRequestListener(serverName);
                return Response.ok(serverName).build();
            } catch (DasException e) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Remote service unavailable").build();
            }
        }
    }

    /**
     * Запрос на аннуляцию регистрации системы сбора данных на сервере загрузки данных.
     *
     * @param serverName имя сервера загрузки данных или null,
     *                   тогда будет аннулирована регистрация на всех серверах,
     *                   прописанных в конфигурационном файле.
     * @return возвращает список серверов, на которых получилось аннулировать регистрацию или ошибку 503 (service unavailable)
     */
    @GET
    @Path("/unregisterListeners")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN + "; charset=UTF-8"})
    public Response unregisterListeners(@QueryParam("server") String serverName) {
        if (serverName == null) {
            try {
                List<String> unregisteredServerNames = listenerServicesBean.unregisterConfigRequestListener();

                return Response.ok(json.toJson(unregisteredServerNames)).build();
            } catch (DasException e) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Remote service unavailable").build();
            }
        } else {
            try {
                listenerServicesBean.unregisterConfigRequestListener(serverName);
                return Response.ok(serverName).build();
            } catch (DasException e) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Remote service unavailable").build();
            }
        }
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
        try {
            Properties properties = PropertiesLoader.loadProperties("app.properties");
            List<String> serverNames = Arrays.asList(properties.getProperty("uploadServerNames").split(" "));
            return Response.ok(json.toJson(serverNames)).build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
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
        return Response.ok(json.toJson(dasSingletonBean.counterNameSet())).build();
    }

    /**
     * Загрузка списка объектов заданных счетчиков и отправка их в базу данных.
     *
     * @param serverName имя сервера загрузки данных.
     * @param counterName имя типа счетчика для поиска объектов, если имя не задано,
     *                    то производится поиск по всем зарегистрированным счетчикам
     *                    в системе сбора данных.
     * @return список всех найденных объектов счетчиков.
     */
    @GET
    @Path("/findObjects")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN + "; charset=UTF-8"})
    public Response findObjects(@QueryParam("server") String serverName, @QueryParam("counter") String counterName) {
        Response response = checkServerName(serverName);
        if (response != null) {
            return response;
        }

        Map<String, List<String>> counterObjects;
        if (counterName == null) {
            logger.info("Find objects for server = {}", serverName);

             counterObjects = dasStatelessBean.getCounterObjects();
        } else {
            if (!dasSingletonBean.containsCounter(counterName)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("unknown counter").build();
            }

            logger.info("Find objects for server = {} and counter = {}", serverName, counterName);

            counterObjects = dasStatelessBean.getCounterObjects(counterName);
        }

        if (counterObjects.isEmpty()) {
            return Response.ok("no counter objects").build();
        } else {
            boolean success = dasStatelessBean.uploadCounterObjects(serverName, counterObjects);

            if (success) {
                return Response.ok(json.toJson(counterObjects)).build();
            } else {
                return Response.ok("Remote service is unavailable").build();
            }
        }
    }

    /**
     * Проверка, существует ли заданный сервер загрузки данных в системе
     *
     * @param serverName имя сервера загрузки данных
     * @return ошибка 400 если имя не задано или не известный сервер
     */
    private Response checkServerName(String serverName) {
        if (serverName == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("parameter \"server\" is required").build();
        }

        try {
            Properties properties = PropertiesLoader.loadProperties("app.properties");
            List<String> serverNames = Arrays.asList(properties.getProperty("uploadServerNames").split(" "));
            if (!serverNames.contains(serverName)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("unknown server").build();
            }
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return null;
    }
}
