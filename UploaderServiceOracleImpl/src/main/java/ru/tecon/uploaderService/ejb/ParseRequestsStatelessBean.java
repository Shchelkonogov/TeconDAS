package ru.tecon.uploaderService.ejb;

import org.slf4j.Logger;
import ru.tecon.uploaderService.ejb.das.ListenerType;
import ru.tecon.uploaderService.ejb.das.RemoteRequest;
import ru.tecon.uploaderService.model.Listener;
import ru.tecon.uploaderService.model.RequestData;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 21.05.2024
 */
@Stateless(name = "oracleRequestStatelessBean")
@LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ParseRequestsStatelessBean {

    private static final String SEL_REQUEST = "select KIND, SERVER_NAME, OBJ_NAME from ADMIN.ARM_TECON_COMMANDS " +
            "where ID = ? and OPC_ID = ?";

    @Inject
    private Logger logger;

    @EJB(beanName = "oracleUploaderSingletonBean")
    private UploaderSingletonBean bean;

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    /**
     * Асинхронный разбор поступающих запросов от базы данных
     *
     * @param id id запроса
     * @param objectId id объекта
     */
    @Asynchronous
    public void acceptRequestAsync(String id, String objectId) {
        logger.info("request from database id = {} objectId = {}", id, objectId);
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SEL_REQUEST)) {
            stm.setString(1, id);
            stm.setString(2, objectId);

            ResultSet res = stm.executeQuery();
            if (res.next()) {
                logger.info("request {} for {}", res.getString("kind"), res.getString("server_name"));

                RequestData requestData = new RequestData(
                        bean.getProperties().getProperty("name"), id,
                        objectId, res.getString("server_name"), res.getString("obj_name")
                );

                switch (res.getString("kind")) {
                    case "AsyncRefresh":
                        acceptRequest(bean.getRemoteListeners(ListenerType.INSTANT_DATA, res.getString("server_name")),
                                requestData);
                        break;
                    case "ForceBrowse":
                        acceptRequest(bean.getRemoteListeners(ListenerType.CONFIGURATION, res.getString("server_name")),
                                requestData);
                        break;
                    case "Subscribe":
                        requestData.getProp().put("sub", "true");
                        acceptRequest(bean.getRemoteListeners(ListenerType.SUBSCRIPTION, res.getString("server_name")),
                                requestData);
                        break;
                    case "Unsubscribe":
                        requestData.getProp().put("sub", "false");
                        acceptRequest(bean.getRemoteListeners(ListenerType.SUBSCRIPTION, res.getString("server_name")),
                                requestData);
                        break;
                    default:
                        logger.warn("Unknown request from db for id = {}", id);
                }
            }
        } catch (SQLException e) {
            logger.warn("Error parse request {} from db ", id, e);
        }
    }

    private void acceptRequest(Set<Listener> listeners, RequestData requestData) {
        for (Listener listener: listeners) {
            switch (listener.getAccessType()) {
                case REMOTE_EJB:
                    if (listener.getProperties().containsKey("jndiProperties") &&
                            (listener.getProperties().get("jndiProperties") instanceof Properties) &&
                            listener.getProperties().containsKey("lookupName")) {
                        try {
                            InitialContext context = new InitialContext(
                                    (Properties) listener.getProperties().get("jndiProperties"));
                            RemoteRequest lookup = (RemoteRequest) context.lookup(
                                    listener.getProperties().getProperty("lookupName"));

                            lookup.acceptAsync(requestData);

                            logger.info("async notify send");
                        } catch (NamingException e) {
                            logger.warn("error send request", e);
                        }
                    } else {
                        logger.warn("error send request. Important properties are lost");
                    }
                    break;
                case CUSTOM_COMMAND:
                    break;
            }
        }
    }
}
