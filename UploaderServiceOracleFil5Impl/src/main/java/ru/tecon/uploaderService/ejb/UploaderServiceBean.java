package ru.tecon.uploaderService.ejb;

import oracle.jdbc.OracleConnection;
import org.slf4j.Logger;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;
import ru.tecon.uploaderService.model.SubscribedObject;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 11.01.2024
 */
@Stateless(name = "oracleFil5UploaderServiceBean", mappedName = "ejb/oracleFil5UploaderServiceBean")
@Remote(UploaderServiceRemote.class)
public class UploaderServiceBean implements UploaderServiceRemote {

    private static final String SEL_SUBSCRIBED_OBJECTS = "select a.ID, a.DISPLAY_NAME from ADMIN.TSA_OPC_OBJECT a " +
            "where extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/Server') = ? " +
                    "and exists(select 1 from ADMIN.TSA_LINKED_OBJECT where OPC_OBJECT_ID = a.ID and SUBSCRIBED = 1)";

    private static final String INSERT_OPC_OBJECT = "insert into ADMIN.TSA_OPC_OBJECT " +
            "values((select ADMIN.GET_GUID_BASE64() from dual), ?, ?, 1)";

    private static final String INSERT_CONFIG = "insert into ADMIN.TSA_OPC_ELEMENT " +
            "values ((select ADMIN.GET_GUID_BASE64() from dual), ?, ?, ?, 1, null)";

    private static final String UPDATE_ARM_TECON_COMMAND = "update ADMIN.ARM_TECON_COMMANDS " +
            "set IS_SUCCESS_EXECUTION = ?, RESULT_DESCRIPTION = ?, END_TIME = sys_extract_utc(current_timestamp) " +
            "where ID = ? and OPC_ID = ?";

    private static final String UPDATE_ARM_COMMAND = "update ADMIN.ARM_COMMANDS " +
            "set IS_SUCCESS_EXECUTION = ?, RESULT_DESCRIPTION = ?, DISPLAY_RESULT_DESCRIPTION = ?, " +
                    "END_TIME = sys_extract_utc(current_timestamp) " +
            "where ID = ? and (IS_SUCCESS_EXECUTION = -1 or IS_SUCCESS_EXECUTION is null)";

    private static final String SELECT_LINKED_PARAMETERS =
            "select c.DISPLAY_NAME, " +
                    "extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo') as sys_info, " +
                    "b.ASPID_OBJECT_ID, " +
                    "d.OBJ_NAME, " +
                    "b.ASPID_PARAM_ID, " +
                    "b.ASPID_AGR_ID, " +
                    "b.MEASURE_UNIT_TRANSFORMER " +
            "from ADMIN.TSA_LINKED_ELEMENT b, ADMIN.TSA_OPC_ELEMENT c, ADMIN.OBJ_OBJECT d " +
            "where b.OPC_ELEMENT_ID in (select id from ADMIN.TSA_OPC_ELEMENT where OPC_OBJECT_ID = ?) " +
                    "and b.OPC_ELEMENT_ID = c.ID " +
                    "and exists(select a.OBJ_ID, a.PAR_ID from ADMIN.DZ_PAR_DEV_LINK a " +
                                "where a.PAR_ID = b.ASPID_PARAM_ID and a.OBJ_ID = b.ASPID_OBJECT_ID) " +
                    "and d.OBJ_ID = b.ASPID_OBJECT_ID " +
                    "and b.ASPID_AGR_ID is not null";

    private static final String SELECT_LINKED_INSTANT_PARAMETERS =
            "select c.DISPLAY_NAME, " +
                    "extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo') as sys_info, " +
                    "b.ASPID_OBJECT_ID, " +
                    "b.ASPID_PARAM_ID, " +
                    "4 as aspid_agr_id " +
            "from ADMIN.TSA_LINKED_ELEMENT b, ADMIN.TSA_OPC_ELEMENT c " +
            "where b.OPC_ELEMENT_ID in (select id from ADMIN.TSA_OPC_ELEMENT where OPC_OBJECT_ID = ?) " +
                    "and b.OPC_ELEMENT_ID = c.ID " +
                    "and exists(select a.OBJ_ID, a.PAR_ID from ADMIN.DZ_PAR_DEV_LINK a " +
                                "where a.PAR_ID = b.ASPID_PARAM_ID and a.OBJ_ID = b.ASPID_OBJECT_ID) " +
                    "and b.ASPID_AGR_ID is null";

    private static final String SELECT_START_DATE = "select TIME_STAMP, PAR_VALUE from ADMIN.DZ_INPUT_START " +
            "where OBJ_ID = ? and PAR_ID = ? and STAT_AGGR = ?";

    private static final String FUNCTION_UPLOAD_DATA = "{call DZ_UTIL1.INPUT_DATA(?)}";

    private static final String INSERT_ASYNC_REFRESH_DATA = "insert into ADMIN.ARM_ASYNC_REFRESH_DATA " +
            "values (?, ?, sys_extract_utc(current_timestamp), ?, ?, ?, sysdate, null, null)";

    private static final String SELECT_COUNTER_OBJECT_ID = "select ID from ADMIN.TSA_OPC_OBJECT " +
            "where extractValue(XMLType('<Group>' || OPC_PATH || '</Group>'), '/Group/Server') = ? " +
                "and extractValue(XMLType('<Group>' || OPC_PATH || '</Group>'), '/Group/ItemName')= ?";

    @Inject
    private Logger logger;

    @Resource
    private EJBContext ejbContext;

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource(name = "jdbc/DataSourceFil")
    private DataSource dsFil;

    @Override
    public List<SubscribedObject> getSubscribedObjects(Set<String> serverNames) {
        logger.info("load subscribed objects for servers {}", serverNames);
        List<SubscribedObject> objects = new ArrayList<>();
        for (String serverName: serverNames) {
            try (Connection connect = ds.getConnection();
                 PreparedStatement stm = connect.prepareStatement(SEL_SUBSCRIBED_OBJECTS)) {

                stm.setString(1, serverName);

                ResultSet res = stm.executeQuery();
                while (res.next()) {
                    objects.add(new SubscribedObject(res.getString("id"), res.getString("display_name"), serverName));
                }
            } catch (SQLException ex) {
                logger.warn("error load subscribed objects for servers {}", serverName, ex);
            }
        }
        logger.info("result load subscribed objects {}", objects);
        return objects;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void uploadObjects(Map<String, List<String>> objects) {
        logger.info("Want to upload objects = {}", objects);
        try (Connection connect = ds.getConnection();
             PreparedStatement stmInsertObject = connect.prepareStatement(INSERT_OPC_OBJECT)) {
            for (Map.Entry<String, List<String>> entry: objects.entrySet()) {
                for (String object: entry.getValue()) {
                    stmInsertObject.setString(1, object);
                    stmInsertObject.setString(2, "<OpcKind>Hda</OpcKind><ItemName>" + object + "</ItemName>" +
                            "<Server>" + entry.getKey() + "</Server>");

                    try {
                        stmInsertObject.executeUpdate();
                        logger.info("Добавлен новый объект: {}", object);
                    } catch(SQLException ex) {
                        if (ex.getErrorCode() == 1) {
                            logger.warn("Запись уже существует {} ", object);
                        } else {
                            logger.warn("Ошибка записи объекта {} ", object, ex);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            logger.warn("Ошибка загрузки объектов ", ex);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int uploadConfig(Set<Config> config, String objectId, String objectName) {
        if (config.isEmpty()) {
            return -1;
        }

        int count = 0;
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(INSERT_CONFIG)) {
            for (Config item: config) {
                String fullDescription = "<ItemName>" + objectName + ":" + item.getName() + "</ItemName>";
                if (!item.getSysInfo().isEmpty()) {
                    fullDescription += "<SysInfo>" + item.getSysInfo() + "</SysInfo>";
                }

                stm.setString(1, item.getName());
                stm.setString(2, fullDescription);
                stm.setString(3, objectId);

                try {
                    stm.executeUpdate();
                    count++;
                    logger.info("Успешная вставка параметра {}", item);
                } catch (SQLException ex) {
                    if (ex.getErrorCode() == 1) {
                        logger.warn("параметр {} уже существует", item);
                    } else {
                        logger.warn("Ошибка записи параметра {} ", item, ex);
                    }
                }
            }
        } catch (SQLException ex) {
            logger.warn("error upload config for object {}", objectName, ex);
            return -1;
        }
        return count;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateCommand(int status, String requestId, String objectId, String messageType, String message) {
        try (Connection connection = ds.getConnection();
             PreparedStatement stmArmTecon = connection.prepareStatement(UPDATE_ARM_TECON_COMMAND);
             PreparedStatement stmArm = connection.prepareStatement(UPDATE_ARM_COMMAND)) {
            stmArmTecon.setInt(1, status);
            stmArmTecon.setString(2, message);
            stmArmTecon.setString(3, requestId);
            stmArmTecon.setString(4, objectId);
            stmArmTecon.executeUpdate();

            stmArm.setInt(1, status);
            stmArm.setString(2, "<" + messageType + ">" + message + "</" + messageType + ">");
            stmArm.setString(3, message);
            stmArm.setString(4, requestId);
            stmArm.executeUpdate();
        } catch (SQLException ex) {
            logger.warn("error update status", ex);
            ejbContext.setRollbackOnly();
        }
    }

    @Override
    public List<DataModel> loadObjectModelWithStartTimes(String id) {
        logger.info("request object model for {}", id);
        List<DataModel> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stmGetLinkedParameters = connect.prepareStatement(SELECT_LINKED_PARAMETERS);
             PreparedStatement stmGetStartDate = connect.prepareStatement(SELECT_START_DATE)) {
            ResultSet resStartDate;

            stmGetLinkedParameters.setString(1, id);

            ResultSet resLinked = stmGetLinkedParameters.executeQuery();
            while (resLinked.next()) {
                Config config;
                if (resLinked.getString("sys_info") != null) {
                    config = new Config(resLinked.getString("display_name"), resLinked.getString("sys_info"));
                } else {
                    config = new Config(resLinked.getString("display_name"));
                }

                DataModel.Builder builder = DataModel.builder(config,
                                resLinked.getInt("aspid_object_id"), resLinked.getInt("aspid_param_id"),
                                resLinked.getInt("aspid_agr_id"))
                        .objectName(resLinked.getString("obj_name"));

                stmGetStartDate.setInt(1, resLinked.getInt("aspid_object_id"));
                stmGetStartDate.setInt(2, resLinked.getInt("aspid_param_id"));
                stmGetStartDate.setInt(3, resLinked.getInt("aspid_agr_id"));

                resStartDate = stmGetStartDate.executeQuery();
                if (resStartDate.next()) {
                    builder.startDateTime(resStartDate.getTimestamp("time_stamp").toLocalDateTime());
                    builder.lastValue(resStartDate.getString("par_value"));
                }

                if ((resLinked.getString("measure_unit_transformer") != null)
                        && !resLinked.getString("measure_unit_transformer").isEmpty()) {
                    builder.incrementValue(resLinked.getString("measure_unit_transformer").substring(2));
                }

                result.add(builder.build());
            }
        } catch (SQLException ex) {
            logger.warn("error load object model for {}", id, ex);
        }
        logger.info("object model for {} {}", id, result);
        return result;
    }

    @Override
    public List<DataModel> loadInstantObjectModel(String id) {
        logger.info("request instant object model for {}", id);
        List<DataModel> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stmGetLinkedParameters = connect.prepareStatement(SELECT_LINKED_INSTANT_PARAMETERS)) {

            stmGetLinkedParameters.setString(1, id);

            ResultSet resLinked = stmGetLinkedParameters.executeQuery();
            while (resLinked.next()) {
                Config config;
                if (resLinked.getString("sys_info") != null) {
                    config = new Config(resLinked.getString("display_name"), resLinked.getString("sys_info"));
                } else {
                    config = new Config(resLinked.getString("display_name"));
                }

                result.add(
                        DataModel.builder(config, resLinked.getInt("aspid_object_id"),
                                        resLinked.getInt("aspid_param_id"), resLinked.getInt("aspid_agr_id"))
                                .build()
                );
            }
        } catch (SQLException ex) {
            logger.warn("error load instant object model for {}", id, ex);
        }
        logger.info("instant object model for {} {}", id, result);
        return result;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void uploadData(List<DataModel> dataModels) {
        try (OracleConnection connect = dsFil.getConnection().unwrap(OracleConnection.class);
             PreparedStatement stmAlter = connect.prepareStatement("alter session set nls_numeric_characters = '.,'");
             CallableStatement stm = connect.prepareCall(FUNCTION_UPLOAD_DATA)) {
            stmAlter.execute();

            List<Object> dataList = new ArrayList<>();
            for (DataModel item: dataModels) {
                for (DataModel.ValueModel value: item.getData()) {
                    Object[] row = {item.getObjectId(), item.getParamId(), item.getAggregateId(),
                                    value.isModified() ? value.getModifyValue() : value.getValue(),
                                    value.getQuality(), Timestamp.valueOf(value.getDateTime()), null};
                    Struct str = connect.createStruct("T_DZ_UTIL_INPUT_DATA_ROW", row);
                    dataList.add(str);
                }
            }

            if (!dataList.isEmpty()) {
                long timer = System.currentTimeMillis();

                Array array = connect.createOracleArray("T_DZ_UTIL_INPUT_DATA", dataList.toArray());

                stm.setArray(1, array);
                stm.execute();

                logger.info("Execute upload data. Values count: {}. Upload time: {} milli seconds",
                        dataList.size(), (System.currentTimeMillis() - timer));
            }
        } catch (SQLException e) {
            logger.warn("error upload data {}", dataModels, e);
        }
    }

    @Override
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void uploadDataAsync(List<DataModel> dataModels) {
        uploadData(dataModels);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int uploadInstantData(String requestId, List<DataModel> paramList) {
        try (Connection connection = ds.getConnection();
             PreparedStatement stmInsert = connection.prepareStatement(INSERT_ASYNC_REFRESH_DATA)) {
            for (DataModel model: paramList) {
                for (DataModel.ValueModel valueModel: model.getData()) {
                    stmInsert.setInt(1, model.getObjectId());
                    stmInsert.setString(2, valueModel.getValue());
                    stmInsert.setInt(3, valueModel.getQuality());
                    stmInsert.setInt(4, model.getParamId());
                    stmInsert.setString(5, requestId);

                    stmInsert.addBatch();
                }
            }

            int[] size = stmInsert.executeBatch();

            logger.info("Execute upload instant data. Values count: {}", size.length);
            return size.length;
        } catch (SQLException ex) {
            logger.warn("Error upload instant data", ex);
            return -1;
        }
    }

    @Override
    public String getCounterObjectId(String counter, String object) {
        logger.info("load counter object id for counter: {} and object: {}", counter, object);
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_COUNTER_OBJECT_ID)) {

            stm.setString(1, counter);
            stm.setString(2, object);

            ResultSet res = stm.executeQuery();
            if (res.next()) {
                return res.getString("id");
            }
        } catch (SQLException ex) {
            logger.warn("error load counter object id", ex);
        }
        return null;
    }
}
