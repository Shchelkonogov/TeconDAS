package ru.tecon.queryBasedDAS.counter.assd;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASStatelessBean;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Maksim Shchelkonogov
 * 21.01.2025
 */
@Path("/assd")
public class ASSDService {

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASStatelessBean bean;

    @POST
    @Path("/sub")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response subTest(String data) {
        logger.info("assd sub data {}", data);
        bean.receiveSubData(ASSDCounterInfo.getInstance().getCounterName(), data);
        return Response.ok().build();
    }
}
