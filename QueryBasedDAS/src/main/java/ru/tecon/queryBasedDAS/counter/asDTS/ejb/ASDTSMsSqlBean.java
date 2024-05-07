package ru.tecon.queryBasedDAS.counter.asDTS.ejb;

import org.slf4j.Logger;
import ru.tecon.uploaderService.model.DataModel;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
@Stateless(name = "ejb/asDTSMsSql")
public class ASDTSMsSqlBean {

    private static final String PRE_OBJECT_NAME = "ИАСДТУ_";

    private static final String SELECT_OBJECTS = "select distinct(station) from ParametersLibraryDisp";
    private static final String SQL_CONFIG = "select name from ParametersLibraryDisp where station = ?";
    private static final String SQL_DATA = "select VACValue, VACtimestamp from bas_ValuesAvgCalc " +
            "where linkID = (select LinkID from ParametersLibraryDisp where station = ? and name = ?) " +
            "and VACtimestamp > ? " +
            "order by VACtimestamp";

    @Inject
    private Logger logger;

    @Resource(name = "jdbc/asDTS")
    private DataSource ds;

    public List<String> getObjects() {
        List<String> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_OBJECTS)) {
            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(PRE_OBJECT_NAME + res.getString(1));
            }
        } catch (SQLException e) {
            logger.warn("Error load objects list", e);
        }
        return result;
    }

    public Set<String> getConfig(String object) {
        logger.info("load config for {}", object);
        Set<String> result = new HashSet<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_CONFIG)) {
            stm.setString(1, object.replace(PRE_OBJECT_NAME, ""));

            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(res.getString(1));
            }
        } catch (SQLException e) {
            logger.warn("Error load config for {}", object, e);
        }
        return result;
    }

    public void loadData(List<DataModel> params, String objectName) {
        logger.info("start load data from ASDTS for {}", objectName);
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_DATA)) {
            stm.setFetchSize(1000);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            for (DataModel item: params) {
                if (item.getStartDateTime() == null) {
                    item.setStartDateTime(LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS));
                }

                stm.setString(1, objectName.replace(PRE_OBJECT_NAME, ""));
                stm.setString(2, item.getParamName());
                // Сравнение со строкой в mssql идет быстрее (не знаю почему, тесты так показали), поэтому заменил на строку
                stm.setString(3, item.getStartDateTime().plusHours(3).format(formatter));

                ResultSet res = stm.executeQuery();
                while (res.next()) {
                    item.addData(res.getString(1), res.getTimestamp(2).toLocalDateTime().minusHours(3));
                }
            }
        } catch (SQLException e) {
            logger.warn("error load data from ASDTS for {}", objectName, e);
        }

        params.removeIf(dataModel -> dataModel.getData().isEmpty());

        logger.info("data from ASDTS loaded for {}", objectName);
    }
}
