package ru.tecon.uploaderService.ejb;

import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import ru.tecon.uploaderService.ejb.das.ConfigRequestRemote;
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

/**
 * @author Maksim Shchelkonogov
 * 16.01.2024
 */
@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ParseRequestsStatelessBean {

    @Language("SQL")
    private static final String SEL_REQUEST = "select kind, server_name, obj_name from admin.arm_tecon_commands " +
            "where id = ? and opc_id = ?";

    @Inject
    private Logger logger;

    @EJB
    private UploaderSingletonBean bean;

    @Resource(name = "jdbc/DataSourceR")
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
                switch (res.getString("kind")) {
                    case "AsyncRefresh":
                        acceptAsyncRefresh();
                        break;
                    case "ForceBrowse":
                        acceptForceBrowse(res.getString("server_name"),
                                new RequestData(bean.getProperties().getProperty("name"),
                                        id,
                                        objectId,
                                        res.getString("server_name"),
                                        res.getString("obj_name")));
                        break;
                    default:
                        logger.warn("Unknown request from db for id = {}", id);
                }
            }
        } catch (SQLException e) {
            logger.warn("Error parse request {} from db ", id, e);
        }
    }

    /**
     * Обработка запроса на конфигурацию
     *
     * @param serverName имя сервера (счетчика)
     * @param requestData данные запроса
     */
    private void acceptForceBrowse(String serverName, RequestData requestData) {
        for (Listener listener: bean.getRemoteListeners()) {
            if (listener.getCounterNameSet().contains(serverName)) {
                try {
                    InitialContext context = new InitialContext(listener.getJndiProperties());
                    ConfigRequestRemote lookup = (ConfigRequestRemote) context.lookup(listener.getLookupName());

                    lookup.acceptAsync(requestData);

                    logger.info("async notify send");
                } catch (NamingException e) {
                    logger.warn("error send request", e);
                }
            }
        }
    }

    private void acceptAsyncRefresh() {

    }
}
