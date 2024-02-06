package ru.tecon.uploaderService.api;

import com.google.gson.Gson;
import ru.tecon.uploaderService.ejb.ParseRequestsStatelessBean;
import ru.tecon.uploaderService.ejb.UploaderSingletonBean;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Maksim Shchelkonogov
 * 16.01.2024
 */
@Path("/")
public class UploadServiceRestApi {

    @EJB
    private ParseRequestsStatelessBean requestsStatelessBean;

    @EJB
    private UploaderSingletonBean uploaderSingletonBean;

    @Inject
    private Gson json;

    /**
     * Принять запрос от базы данных
     *
     * @param id id запроса
     * @param objectId id объекта
     * @return статус принятия запроса
     */
    @GET
    @Path("/request")
    public Response acceptDbRequest(@QueryParam("id") String id, @QueryParam("objectId") String objectId) {
        requestsStatelessBean.acceptRequestAsync(id, objectId);
        return Response.ok().build();
    }

    /**
     * Получение списка подписанных систем сбора данных
     *
     * @return список систем сбора данных
     */
    @GET
    @Path("/getRegisterListeners")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRegisterListeners() {
        return Response.ok(json.toJson(uploaderSingletonBean.getDasListeners())).build();
    }
}
