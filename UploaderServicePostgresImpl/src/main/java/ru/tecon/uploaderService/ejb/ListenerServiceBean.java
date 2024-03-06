package ru.tecon.uploaderService.ejb;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import ru.tecon.uploaderService.ejb.das.ListenerServiceRemote;
import ru.tecon.uploaderService.ejb.das.ListenerType;
import ru.tecon.uploaderService.model.Listener;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Maksim Shchelkonogov
 * 17.01.2024
 */
@Stateless(name = "listenerServiceBean", mappedName = "ejb/listenerServiceBean")
@Remote(ListenerServiceRemote.class)
public class ListenerServiceBean implements ListenerServiceRemote {

    private static final String SEL_CHECK_CONTAINS_LISTENER_URL = "select 1 from admin.opc_base where server_name = ?";
    private static final String INS_LISTENER_URL = "insert into admin.opc_base values ('', ?, ?)";
    private static final String UPD_LISTENER_URL = "update admin.opc_base set url = ? where server_name = ?";
    private static final String DEL_LISTENER_URL = "delete from admin.opc_base where server_name = ?";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Inject
    private Logger logger;

    @Inject
    @ConfigProperty(name = "payara.instance.http.port")
    private String port;

    @EJB
    private UploaderSingletonBean uploaderSingletonBean;

    @Override
    public void addListener(Listener listener) {
        logger.info("New listener {}", listener);

        uploaderSingletonBean.putListener(listener.getDasName(), listener);

        registerListenerInDb(listener);
    }

    @Override
    public boolean containsListener(String dasName, ListenerType type) {
        return uploaderSingletonBean.containsListener(dasName, type);
    }

    @Override
    public void removeListener(String dasName, ListenerType type) {
        logger.info("Remove listener {}", dasName);

        Listener listener = uploaderSingletonBean.removeListener(dasName, type);

        if ((listener != null) && !uploaderSingletonBean.containsListener(dasName)) {
            unregisterListenerInDb(listener);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    private void registerListenerInDb(Listener listener) {
        try (Connection connect = ds.getConnection();
             PreparedStatement checkStm = connect.prepareStatement(SEL_CHECK_CONTAINS_LISTENER_URL);
             PreparedStatement insStm = connect.prepareStatement(INS_LISTENER_URL);
             PreparedStatement updStm = connect.prepareStatement(UPD_LISTENER_URL)) {
            for (String counter: listener.getCounterNameSet()) {
                checkStm.setString(1, counter);

                String url = "http://" + InetAddress.getLocalHost().getHostName() + ":" + port + "/uploaderService/api/request?id=[id]&objectId=[objectId]";

                ResultSet res = checkStm.executeQuery();
                if (res.next()) {
                    updStm.setString(1, url);
                    updStm.setString(2, counter);

                    updStm.executeUpdate();
                } else {
                    insStm.setString(1, counter);
                    insStm.setString(2, url);

                    insStm.executeUpdate();
                }
            }
        } catch (SQLException | UnknownHostException e) {
            logger.warn("Error register listener in database", e);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    private void unregisterListenerInDb(Listener listener) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(DEL_LISTENER_URL)) {
            for (String counter: listener.getCounterNameSet()) {
                stm.setString(1, counter);

                stm.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warn("Error unregister listener in database", e);
        }
    }
}
