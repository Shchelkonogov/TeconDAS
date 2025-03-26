package ru.tecon.uploaderService.ejb;

import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;
import ru.tecon.uploaderService.model.SubscribedObject;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
@Stateless(name = "uploaderServiceBean", mappedName = "ejb/uploaderServiceBean")
@Remote(UploaderServiceRemote.class)
public class UploaderServiceBean implements UploaderServiceRemote {

    private static final String SEL_SUBSCRIBED_OBJECTS = "select a.id, a.display_name from admin.tsa_opc_object a " +
            "where cast((xpath('/root/Server/text()', xmlelement(name root, opc_path::xml)))[1] as text) = ? " +
                "and exists(select 1 from admin.tsa_linked_object where opc_object_id = a.id and subscribed = '1')";

    private static final String INSERT_OPC_OBJECT = "insert into admin.tsa_opc_object " +
            "values((select admin.get_guid_base64()), ?, ?, 1)";

    private static final String INSERT_CONFIG = "insert into admin.tsa_opc_element " +
            "values ((select admin.get_guid_base64()), ?, ?, ?, 1, null)";

    private static final String UPDATE_ARM_TECON_COMMAND = "update admin.arm_tecon_commands " +
            "set is_success_execution = ?, result_description = ?, end_time = current_timestamp where id = ? and opc_id = ?";

    private static final String UPDATE_ARM_COMMAND = "update admin.arm_commands " +
            "set is_success_execution = ?, result_description = ?, display_result_description = ?, " +
            "end_time = current_timestamp where id = ? and (is_success_execution = -1 or is_success_execution is null)";

    private static final String SELECT_LINKED_PARAMETERS =
            "select c.display_name, " +
                    "cast((xpath('/root/SysInfo/text()', xmlelement(name root, opc_path::xml)))[1] as text) as sys_info, " +
                    "b.aspid_object_id, " +
                    "d.obj_name, " +
                    "b.aspid_param_id, " +
                    "b.aspid_agr_id, " +
                    "b.measure_unit_transformer " +
            "from admin.tsa_linked_element b, admin.tsa_opc_element c, admin.obj_object d " +
            "where b.opc_element_id in (select id from admin.tsa_opc_element where opc_object_id = ?) " +
                    "and b.opc_element_id = c.id " +
                    "and exists(select a.obj_id, a.par_id from admin.dz_par_dev_link a " +
                                "where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
                    "and d.obj_id = b.aspid_object_id " +
                    "and b.aspid_agr_id is not null";

    private static final String SELECT_LINKED_INSTANT_PARAMETERS =
            "select c.display_name, " +
                    "cast((xpath('/root/SysInfo/text()', xmlelement(name root, opc_path::xml)))[1] as text) as sys_info, " +
                    "b.aspid_object_id, " +
                    "b.aspid_param_id, " +
                    "4 as aspid_agr_id, " +
                    "b.measure_unit_transformer " +
            "from admin.tsa_linked_element b, admin.tsa_opc_element c " +
            "where b.opc_element_id in (select id from admin.tsa_opc_element where opc_object_id = ?) " +
                    "and b.opc_element_id = c.id " +
                    "and exists(select a.obj_id, a.par_id from admin.dz_par_dev_link a " +
                                "where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
                    "and b.aspid_agr_id is null";

    private static final String SELECT_START_DATE = "select time_stamp, par_value from admin.dz_input_start " +
            "where obj_id = ? and par_id = ? and stat_aggr = ?";

    private static final String FUNCTION_UPLOAD_DATA = "call dz_util.input_data(?)";

    private static final String INSERT_ASYNC_REFRESH_DATA = "insert into admin.arm_async_refresh_data " +
            "values (?, ?, current_timestamp at time zone 'utc', ?, ?, ?, current_timestamp, null, null)";

    private static final String SELECT_COUNTER_OBJECT_ID = "select id from admin.tsa_opc_object " +
            "where cast((xpath('/root/Server/text()', xmlelement(name root, opc_path::xml)))[1] as text) = ? " +
                "and cast((xpath('/root/ItemName/text()', xmlelement(name root, opc_path::xml)))[1] as text) = ?";

    @Inject
    private Logger logger;

    @Resource
    private EJBContext ejbContext;

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource(name = "jdbc/DataSourceR")
    private DataSource dsRead;

    @Override
    public List<SubscribedObject> getSubscribedObjects(Set<String> serverNames) {
        logger.info("load subscribed objects for servers {}", serverNames);
        List<SubscribedObject> objects = new ArrayList<>();
        for (String serverName: serverNames) {
            try (Connection connect = dsRead.getConnection();
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
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
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
                        if (ex.getSQLState().equals("23505")) {
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
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
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
                    if (ex.getSQLState().equals("23505")) {
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
        try (Connection connect = dsRead.getConnection();
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
        try (Connection connect = dsRead.getConnection();
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

                DataModel.Builder builder = DataModel.builder(config, resLinked.getInt("aspid_object_id"),
                        resLinked.getInt("aspid_param_id"), resLinked.getInt("aspid_agr_id"));

                if ((resLinked.getString("measure_unit_transformer") != null)
                        && !resLinked.getString("measure_unit_transformer").isEmpty()) {
                    builder.incrementValue(resLinked.getString("measure_unit_transformer").substring(2));
                }

                result.add(builder.build());
            }
        } catch (SQLException ex) {
            logger.warn("error load instant object model for {}", id, ex);
        }
        logger.info("instant object model for {} {}", id, result);
        return result;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void uploadData(List<DataModel> dataModels) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(FUNCTION_UPLOAD_DATA)) {
            List<Object> dataList = new ArrayList<>();
            for (DataModel item: dataModels) {
                for (DataModel.ValueModel value: item.getData()) {
                    if (value.getDateTime() != null) {
                        String s = new StringJoiner(", ", "(", ")")
                                .add(String.valueOf(item.getParamId()))
                                .add(String.valueOf(item.getObjectId()))
                                .add(String.valueOf(item.getAggregateId()))
                                .add(value.isModified() ? value.getModifyValue() : value.getValue())
                                .add(String.valueOf(value.getQuality()))
                                .add(formatter.format(value.getDateTime()))
                                .toString();

                        PGobject pGobject = new PGobject();
                        pGobject.setType("dz_find_cond.input_data_rec");
                        pGobject.setValue(s);

                        dataList.add(pGobject);
                    }
                }
            }

            if (!dataList.isEmpty()) {
                long timer = System.currentTimeMillis();

                Array array = connect.createArrayOf("dz_find_cond.input_data_rec", dataList.toArray());

                stm.setArray(1, array);
                stm.execute();

                logger.info("Execute upload data. Values count: {}. Upload time: {} milli seconds", dataList.size(), (System.currentTimeMillis() - timer));
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
        try (Connection connect = dsRead.getConnection();
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
