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
import java.sql.*;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 30.01.2024
 */
@Stateless(name = "oracleListenerServiceBean", mappedName = "ejb/oracleListenerServiceBean")
@Remote(ListenerServiceRemote.class)
public class ListenerServiceBean implements ListenerServiceRemote {

    private static final String SEL_CHECK_CONTAINS_LISTENER_URL = "select 1 from ADMIN.OPC_BASE where SERVER_NAME = ?";
    private static final String INS_LISTENER_URL = "insert into ADMIN.OPC_BASE values ('fil2.mipcnet.org', ?, ?)";
    private static final String UPD_LISTENER_URL = "update ADMIN.OPC_BASE set URL = ? where SERVER_NAME = ?";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Inject
    private Logger logger;

    @Inject
    @ConfigProperty(name = "payara.instance.https.port")
    private String port;

    @EJB(beanName = "oracleUploaderSingletonBean")
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

        Set<String> removeCounters = uploaderSingletonBean.removeListener(dasName, type);

        if (!removeCounters.isEmpty()) {
            logger.info("Remove counters {}", removeCounters);
            unregisterListenerInDb(removeCounters);
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

                String url = "https://" + InetAddress.getLocalHost().getCanonicalHostName() + ":" + port +
                        "/oracleUploaderService/api/request?id=[id]&objectId=[objectId]";

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
    private void unregisterListenerInDb(Set<String> removeCounters) {
        try (Connection connect = ds.getConnection();
             PreparedStatement checkStm = connect.prepareStatement(SEL_CHECK_CONTAINS_LISTENER_URL);
             PreparedStatement stm = connect.prepareStatement(UPD_LISTENER_URL)) {
            for (String counter: removeCounters) {
                checkStm.setString(1, counter);

                ResultSet res = checkStm.executeQuery();
                if (res.next()) {
                    stm.setNull(1, Types.VARCHAR);
                    stm.setString(2, counter);

                    stm.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.warn("Error unregister listener in database", e);
        }
    }
}
