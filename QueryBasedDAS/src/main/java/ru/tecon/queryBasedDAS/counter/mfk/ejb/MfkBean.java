package ru.tecon.queryBasedDAS.counter.mfk.ejb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.mfk.MfkConsoleController;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 03.10.2024
 */
@Stateless(name = "mfk", mappedName = "ejb/mfk")
public class MfkBean {

    private static final int httpTimeout = 5;
    private static final Pattern PATTERN_IPV4 = Pattern.compile("_(?<ip>((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?))_", Pattern.CASE_INSENSITIVE);

    private static final String SELECT_OBJECTS = "select concat(b.name, '_', a.name) " +
                                                    "from mfk_object a, mfk_server b " +
                                                        "where a.server_id = b.id";
    private static final String SELECT_CONFIG = "select name " +
                                                    "from mfk_config " +
                                                        "where server_id = ?";
    private static final String SELECT_DATA = "select value, date, mc.name from mfk_data md " +
                                                "join mfk_config mc " +
                                                    "on mc.id = md.param_id " +
                                                "join public.mfk_server ms " +
                                                    "on ms.id = mc.server_id " +
                                                "join public.mfk_object mo " +
                                                    "on md.object_id = mo.id " +
                                                        "where ms.name = ? " +
                                                            "and mc.name = any(?) " +
                                                            "and mo.name = ?" +
                                                            "and date > ? " +
                                                        "order by date";
    private static final String SELECT_SERVER = "select id, scheme, host, port, path from mfk_server where name = ?";
    private static final String SELECT_ALL_SERVER = "select id, name, scheme, host, port, path from mfk_server";
    private static final String SELECT_MAX_DATE = "select mc.name as param_name, max(date) as date from mfk_data md " +
                                                    "join mfk_config mc " +
                                                        "on mc.id = md.param_id " +
                                                    "join public.mfk_server ms " +
                                                        "on ms.id = mc.server_id " +
                                                    "join public.mfk_object mo " +
                                                        "on md.object_id = mo.id " +
                                                            "where ms.name = ? " +
                                                                "and mo.name = ?" +
                                                            "group by mc.name";
    private static final String SELECT_LAST_DATA = "select value from mfk_data md " +
                                                        "join mfk_config mc " +
                                                            "on mc.id = md.param_id " +
                                                        "join public.mfk_server ms " +
                                                            "on ms.id = mc.server_id " +
                                                        "join public.mfk_object mo " +
                                                            "on md.object_id = mo.id " +
                                                                "where ms.name = ? " +
                                                                    "and mc.name = ? " +
                                                                    "and mo.name = ?" +
                                                                    "and date = ?";
    private static final String SELECT_GROUP_DATA = "select mop.name, mop.count from mfk_object_package mop " +
                                                        "join mfk_object mo " +
                                                            "on mop.object_id = mo.id " +
                                                        "join mfk_server ms " +
                                                            "on mo.server_id = ms.id " +
                                                                "where ms.name = ? " +
                                                                    "and mo.name = ? " +
                                                                    "and mop.date = ?";

    @Inject
    private Logger logger;

    @Inject
    private Gson json;

    @Inject
    private ObjectMapper om;

    @Resource(name = "jdbc/mfk")
    private DataSource ds;

    public List<String> getObjects() {
        List<String> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_OBJECTS)) {
            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(res.getString(1));
            }
        } catch (SQLException e) {
            logger.warn("Error load objects list", e);
        }
        return result;
    }

    public Set<Config> getConfig(String object) {
        logger.info("load config for {}", object);
        Set<Config> result = new HashSet<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stmServer = connect.prepareStatement(SELECT_SERVER);
             PreparedStatement stm = connect.prepareStatement(SELECT_CONFIG)) {
            stmServer.setString(1, object.split("_")[0]);
            ResultSet resServer = stmServer.executeQuery();
            if (resServer.next()) {
                // Загрузка исторической конфигурации
                stm.setInt(1, resServer.getInt("id"));
                ResultSet res = stm.executeQuery();
                while (res.next()) {
                    String value = res.getString(1);
                    if (value.contains("::")) {
                        String[] split = value.split("::");
                        result.add(new Config(split[0], split[1]));
                    } else {
                        result.add(new Config(value));
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error load config for {}", object, e);
        }

        // Загрузка мгновенной конфигурации
        Map<String, String> parameters = new HashMap<>();
        Matcher m = PATTERN_IPV4.matcher(object);
        if (m.find()) {
            parameters.put("url", m.group("ip"));

            object = object.split("_")[0];

            Map<String, URI> uri = getURI(Set.of(object), List.of("api", "instantConfig"), parameters);

            if (!uri.isEmpty()) {
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(httpTimeout * 60 * 1000)
                        .setConnectionRequestTimeout(httpTimeout * 60 * 1000)
                        .setSocketTimeout(httpTimeout * 60 * 1000).build();

                try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build()) {
                    HttpGet httpGet = new HttpGet(uri.get(object));

                    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            List<String> config = json.fromJson(
                                    EntityUtils.toString(response.getEntity()),
                                    new TypeToken<ArrayList<String>>() {}.getType()
                            );

                            for (String value: config) {
                                if (value.contains("::")) {
                                    String[] split = value.split("::");
                                    result.add(new Config(split[0], split[1]));
                                } else {
                                    result.add(new Config(value));
                                }
                            }
                        } else {
                            logger.warn("Error request instance config. Error code {}", response.getStatusLine().getStatusCode());
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error request instance config", e);
                }
            }
        } else {
            logger.warn("Error parse ip address {}", object);
        }

        return result;
    }

    public void loadData(List<DataModel> params, String objectName) {
        logger.info("start load data from mfk for {}", objectName);
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_DATA)) {
            stm.setFetchSize(1000);

            String[] split = objectName.split("_");
            String controllerName = split[0];
            String controllerObjectName = split[1] + "_" + split[2];

            LocalDateTime minDate = null;
            Map<String, DataModel> parNames = new HashMap<>();
            LocalDateTime minDateJournal = null;
            Map<String, DataModel> parNamesJournal = new HashMap<>();
            for (DataModel item: params) {
                if ((item.getStartDateTime() == null) || item.getStartDateTime().isBefore(LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS))) {
                    item.setStartDateTime(LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS));
                }

                if (item.getAggregateId() == 0) {
                    if (minDateJournal == null) {
                        minDateJournal = item.getStartDateTime();
                    } else {
                        if (item.getStartDateTime().isBefore(minDateJournal)) {
                            minDateJournal = item.getStartDateTime();
                        }
                    }

                    parNamesJournal.put(item.getParamName() + "::" + item.getParamSysInfo(), item);
                } else {
                    if (minDate == null) {
                        minDate = item.getStartDateTime();
                    } else {
                        if (item.getStartDateTime().isBefore(minDate)) {
                            minDate = item.getStartDateTime();
                        }
                    }

                    parNames.put(item.getParamName() + "::" + item.getParamSysInfo(), item);
                }
            }

            selectData(connect, stm, controllerName, controllerObjectName, minDate, parNames);
            selectData(connect, stm, controllerName, controllerObjectName, minDateJournal, parNamesJournal);
        } catch (SQLException e) {
            logger.warn("error load data from mfk for {}", objectName, e);
        }

        params.removeIf(dataModel -> dataModel.getData().isEmpty());

        for (DataModel dataModel: params) {
            switch (dataModel.getParamSysInfo()) {
                case "5":
                    addLastValue(dataModel, "1");
                    break;
                case "6":
                    addLastValue(dataModel, "60");
                    break;
                case "7":
                    addLastValue(dataModel, "3600");
                    break;
            }
        }

        logger.info("data from mfk loaded for {}", objectName);
    }

    private void selectData(Connection connect, PreparedStatement stm, String controllerName,
                            String controllerObjectName, LocalDateTime minDate, Map<String, DataModel> parNames) throws SQLException {
        if (!parNames.isEmpty() && (minDate != null)) {
            stm.setString(1, controllerName);
            stm.setArray(2, connect.createArrayOf("VARCHAR", parNames.keySet().toArray(new String[0])));
            stm.setString(3, controllerObjectName);
            stm.setTimestamp(4, Timestamp.valueOf(minDate.plusSeconds(1)));

            ResultSet res = stm.executeQuery();
            while (res.next()) {
                DataModel model = parNames.get(res.getString("name"));
                if (!res.getTimestamp("date").toLocalDateTime().isBefore(model.getStartDateTime())) {
                    model.addData(res.getString("value"), res.getTimestamp("date").toLocalDateTime());
                }
            }
        }
    }

    /**
     * Добавление к всем значением последнего известного (начиная с даты этого последнего известного),
     * а так же умножается на переданное число
     *
     * @param dataModel данные
     * @param multiplyValue значение на которое умножается
     */
    private void addLastValue(DataModel dataModel, String multiplyValue) {
        BigDecimal addValue = new BigDecimal(dataModel.getLastValue() == null ? "0" : dataModel.getLastValue());
        BigDecimal newValue;
        LocalDateTime localDateTime = dataModel.getStartDateTime();
        for (DataModel.ValueModel valueModel: dataModel.getData()) {
            if (valueModel.getDateTime().isAfter(localDateTime)) {
                newValue = new BigDecimal(valueModel.getValue())
                        .multiply(new BigDecimal(multiplyValue))
                        .add(addValue);
                valueModel.setModifyValue(newValue.toString());
                addValue = newValue;
            }
        }
    }

    public void loadInstantData(List<DataModel> params, String objectName) throws DasException {
        logger.info("start load instant data from mfk for {}", objectName);

        Map<String, String> parameters = new HashMap<>();
        Matcher m = PATTERN_IPV4.matcher(objectName);
        if (m.find()) {
            parameters.put("url", m.group("ip"));

            objectName = objectName.split("_")[0];

            Map<String, URI> uri = getURI(Set.of(objectName), List.of("api", "instantData"), parameters);

            if (!uri.isEmpty()) {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpPost httpPost = new HttpPost(uri.get(objectName));

                    List<String> paramsList = params.stream()
                            .map(dataModel -> dataModel.getParamName() + "::" + dataModel.getParamSysInfo())
                            .collect(Collectors.toList());

                    logger.info("request instant data for {}", paramsList);

                    httpPost.setHeader("Content-type", "application/json");
                    httpPost.setEntity(new StringEntity(json.toJson(paramsList)));

                    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            Map<String, String> instantData = json.fromJson(
                                    EntityUtils.toString(response.getEntity()),
                                    new TypeToken<HashMap<String, String>>() {}.getType()
                            );

                            logger.info("Received instant data {}", instantData);
                            for (Map.Entry<String, String> entry: instantData.entrySet()) {
                                for (DataModel model: params) {
                                    if (model.getParamName().startsWith(entry.getKey() + ":Текущие данные")) {
                                        model.addData(entry.getValue(), LocalDateTime.now(ZoneOffset.UTC));
                                        break;
                                    }
                                }
                            }

                            params.removeIf(dataModel -> dataModel.getData().isEmpty());
                        } else {
                            logger.warn("Error request instance data. Error code {}", response.getStatusLine().getStatusCode());
                            throw new DasException(EntityUtils.toString(response.getEntity()));
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error request instance data", e);
                    throw new DasException("Ошибка обращения к контроллеру мфк " + objectName);
                }
            }
        } else {
            logger.warn("Error parse ip address {}", objectName);
        }

        logger.info("instant data from mfk loaded for {}", objectName);
    }

    public List<String> getLocked() {
        List<String> result = new ArrayList<>();
        List<MfkServer> servers = new ArrayList<>();

        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_ALL_SERVER)) {
            ResultSet res = stm.executeQuery();
            while (res.next()) {
                servers.add(new MfkServer(
                        res.getString("name"),
                        res.getString("path"),
                        res.getString("scheme"),
                        res.getString("host"),
                        res.getInt("port")
                ));
            }
        } catch (SQLException e) {
            logger.warn("Error request locked", e);
        }

        for (MfkServer server: servers) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                ArrayList<String> path = new ArrayList<>(Arrays.asList(server.path.split("/")));
                path.removeIf(String::isEmpty);
                path.add("api");
                path.add("locked");

                URI build = new URIBuilder()
                        .setScheme(server.scheme)
                        .setHost(server.host)
                        .setPort(server.port)
                        .setPathSegments(path)
                        .build();

                HttpGet httpGet = new HttpGet(build);

                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        List<String> locked = json.fromJson(
                                EntityUtils.toString(response.getEntity()),
                                new TypeToken<List<String>>() {}.getType()
                        );

                        locked.forEach(s -> result.add(server.name + "_" + s + "_"));
                    } else {
                        logger.warn("Error request locked. Error code {}", response.getStatusLine().getStatusCode());
                    }
                }
            } catch (IOException | URISyntaxException e) {
                logger.warn("Error request locked", e);
            }
        }

        return result;
    }

    public void resetTraffic(String objectName) throws DasException {
        logger.info("reset traffic for {}", objectName);

        Map<String, String> parameters = new HashMap<>();
        Matcher m = PATTERN_IPV4.matcher(objectName);
        if (m.find()) {
            parameters.put("url", m.group("ip"));

            objectName = objectName.split("_")[0];

            Map<String, URI> uri = getURI(Set.of(objectName), List.of("api", "reset"), parameters);

            if (!uri.isEmpty()) {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpPost httpPost = new HttpPost(uri.get(objectName));

                    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                        if (response.getStatusLine().getStatusCode() != 200) {
                            logger.warn("Error reset traffic. Error code {}", response.getStatusLine().getStatusCode());
                            throw new DasException(EntityUtils.toString(response.getEntity()));
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error reset traffic", e);
                    throw new DasException("Ошибка обращения к контроллеру мфк " + objectName);
                }
            }
        } else {
            logger.warn("Error parse ip address {}", objectName);
        }
    }

    public String getTraffic(String objectName) {
        logger.info("get traffic for {}", objectName);

        Map<String, String> parameters = new HashMap<>();
        Matcher m = PATTERN_IPV4.matcher(objectName);
        if (m.find()) {
            parameters.put("url", m.group("ip"));

            objectName = objectName.split("_")[0];

            Map<String, URI> uri = getURI(Set.of(objectName), List.of("api", "getTraffic"), parameters);

            if (!uri.isEmpty()) {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpGet httpGet = new HttpGet(uri.get(objectName));

                    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            return EntityUtils.toString(response.getEntity());
                        } else {
                            logger.warn("Error get traffic. Error code {}", response.getStatusLine().getStatusCode());
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error get traffic", e);
                }
            }
        } else {
            logger.warn("Error parse ip address {}", objectName);
        }

        return "Неопределенно";
    }

    public Map<String, String> getTraffic(Map<String, List<String>> objectNames) {
        logger.info("get traffic for {}", objectNames);
        Map<String, String> result = new HashMap<>();

        Map<String, URI> uri = getURI(objectNames.keySet(), List.of("api", "getTraffic"), Map.of());

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            for (Map.Entry<String, List<String>> entry: objectNames.entrySet()) {
                if (uri.containsKey(entry.getKey())) {
                    HttpPost httpPost = new HttpPost(uri.get(entry.getKey()));


                    Set<String> ipSet = entry.getValue().stream()
                            .map(s -> {
                                Matcher m = PATTERN_IPV4.matcher(s);
                                if (m.find()) {
                                    return m.group("ip");
                                }
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());


                    logger.info("start read traffic for ip {} mfk {}", ipSet, entry.getKey());

                    httpPost.setHeader("Content-type", "application/json");
                    httpPost.setEntity(new StringEntity(json.toJson(ipSet)));

                    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            Map<String, String> traffic = om.readValue(
                                    EntityUtils.toString(response.getEntity()),
                                    new TypeReference<Map<String, String>>() {}
                            );
                            traffic.forEach((k, v) -> {
                                result.put(entry.getKey() + "_" + k + "_", v);
                            });
                        } else {
                            logger.warn("Error get traffic. Error code {}", response.getStatusLine().getStatusCode());
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Error get traffic", e);
        }

        return result;
    }

    public List<StatData.LastValue> getLastValues(String objectName) {
        logger.info("start load last values from mfk for {}", objectName);
        List<StatData.LastValue> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stmMaxDate = connect.prepareStatement(SELECT_MAX_DATE);
             PreparedStatement stmLastData = connect.prepareStatement(SELECT_LAST_DATA)) {
            String[] split = objectName.split("_");
            String controllerName = split[0];
            String controllerObjectName = split[1] + "_" + split[2];

            stmMaxDate.setString(1, controllerName);
            stmMaxDate.setString(2, controllerObjectName);

            ResultSet resMaxDate = stmMaxDate.executeQuery();
            while (resMaxDate.next()) {
                stmLastData.setString(1, controllerName);
                stmLastData.setString(2, resMaxDate.getString("param_name"));
                stmLastData.setString(3, controllerObjectName);
                stmLastData.setTimestamp(4, resMaxDate.getTimestamp("date"));

                ResultSet resLastData = stmLastData.executeQuery();
                if (resLastData.next()) {
                    result.add(StatData.LastValue.of(
                            resMaxDate.getString("param_name"),
                            resLastData.getString("value"),
                            resMaxDate.getTimestamp("date").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warn("error load last values from mfk for {}", objectName, e);
        }
        return result;
    }

    /**
     * Получение статистики по переданным группам
     *
     * @param objectName имя объекта
     * @param localDate дата, за которую смотреть статистику
     * @return статистика по переданным группам (имя группы -> количество полученных групп)
     */
    public Map<String, String> getGroupData(String objectName, LocalDate localDate) {
        logger.info("start load group data from mfk for {} and date {}", objectName, localDate);
        Map<String, String> result = new HashMap<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stmGroupData = connect.prepareStatement(SELECT_GROUP_DATA)) {
            String[] split = objectName.split("_");
            String controllerName = split[0];
            String controllerObjectName = split[1] + "_" + split[2];

            stmGroupData.setString(1, controllerName);
            stmGroupData.setString(2, controllerObjectName);
            stmGroupData.setDate(3, Date.valueOf(localDate));

            ResultSet res = stmGroupData.executeQuery();
            while (res.next()) {
                result.put(res.getString("name"), res.getString("count"));
            }
        } catch (SQLException e) {
            logger.warn("error load group data from mfk for {}", objectName, e);
        }
        return result;
    }

    public List<MfkConsoleController.ObjectInfoModel> getSysInfo(String objectName) {
        List<MfkConsoleController.ObjectInfoModel> result = new ArrayList<>();
        logger.info("start load sys info from mfk for {}", objectName);

        Map<String, String> parameters = new HashMap<>();
        Matcher m = PATTERN_IPV4.matcher(objectName);
        if (m.find()) {
            parameters.put("url", m.group("ip"));

            objectName = objectName.split("_")[0];

            Map<String, URI> uri = getURI(Set.of(objectName), List.of("api", "sysInfo"), parameters);

            if (!uri.isEmpty()) {
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(httpTimeout * 60 * 1000)
                        .setConnectionRequestTimeout(httpTimeout * 60 * 1000)
                        .setSocketTimeout(httpTimeout * 60 * 1000).build();

                try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build()) {
                    HttpGet httpGet = new HttpGet(uri.get(objectName));

                    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            Map<String, Map<String, Boolean>> sysInfo = om.readValue(
                                    EntityUtils.toString(response.getEntity()),
                                    new TypeReference<Map<String, Map<String, Boolean>>>() {
                                    }
                            );

                            sysInfo.forEach((k, v) -> {
                                if (v.size() == 1) {
                                    for (Map.Entry<String, Boolean> entry: v.entrySet()) {
                                        result.add(new MfkConsoleController.ObjectInfoModel(k, entry.getKey(), entry.getValue()));
                                    }
                                }
                            });

                            logger.info("sys info {}", result);
                        } else {
                            logger.warn("Error request sys info. Error code {}", response.getStatusLine().getStatusCode());
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error request sys info", e);
                }
            }
        } else {
            logger.warn("Error parse ip address {}", objectName);
        }

        return result;
    }

    public void synchronizeDate(String objectName) {
        logger.info("start synchronize date from mfk for {}", objectName);

        Map<String, String> parameters = new HashMap<>();
        Matcher m = PATTERN_IPV4.matcher(objectName);
        if (m.find()) {
            parameters.put("url", m.group("ip"));

            objectName = objectName.split("_")[0];

            Map<String, URI> uri = getURI(Set.of(objectName), List.of("api", "synchronizeDate"), parameters);

            if (!uri.isEmpty()) {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpPost httpPost = new HttpPost(uri.get(objectName));

                    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                        if (response.getStatusLine().getStatusCode() != 200) {
                            logger.warn("Error synchronize date. Error code {}", response.getStatusLine().getStatusCode());
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error synchronize date", e);
                }
            }
        } else {
            logger.warn("Error parse ip address {}", objectName);
        }
    }

    public void writeSysInfo(String objectName, List<MfkConsoleController.ObjectInfoModel> sysInfo) {
        logger.info("start write sys info to mfk for {}", objectName);

        Map<String, String> parameters = new HashMap<>();
        Matcher m = PATTERN_IPV4.matcher(objectName);
        if (m.find()) {
            parameters.put("url", m.group("ip"));

            objectName = objectName.split("_")[0];

            Map<String, URI> uri = getURI(Set.of(objectName), List.of("api", "sysInfo"), parameters);

            if (!uri.isEmpty()) {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpPost httpPost = new HttpPost(uri.get(objectName));

                    Map<String, String> sysInfoMap = sysInfo.stream()
                            .filter(MfkConsoleController.ObjectInfoModel::isChange)
                            .collect(Collectors.toMap(
                                    MfkConsoleController.ObjectInfoModel::getName,
                                    MfkConsoleController.ObjectInfoModel::getValue
                            ));
                    logger.info("start write sys info {} to mfk for {}", sysInfoMap, objectName);

                    httpPost.setHeader("Content-type", "application/json");
                    httpPost.setEntity(new StringEntity(json.toJson(sysInfoMap)));

                    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                        if (response.getStatusLine().getStatusCode() != 200) {
                            logger.warn("Error write sys info. Error code {}", response.getStatusLine().getStatusCode());
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error write sys info", e);
                }
            }
        } else {
            logger.warn("Error parse ip address {}", objectName);
        }
    }

    private Map<String, URI> getURI(Set<String> objectNameList,
                                    List<String> paths,
                                    Map<String, String> parameters) {
        Map<String, URI> result = new HashMap<>();

        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_SERVER)) {
            for (String objectName: objectNameList) {
                stm.setString(1, objectName);
                ResultSet res = stm.executeQuery();
                if (res.next()) {
                    ArrayList<String> path = new ArrayList<>(Arrays.asList(res.getString("path").split("/")));
                    path.removeIf(String::isEmpty);
                    path.addAll(paths);

                    URIBuilder uriBuilder = new URIBuilder()
                            .setScheme(res.getString("scheme"))
                            .setHost(res.getString("host"))
                            .setPort(res.getInt("port"))
                            .setPathSegments(path);

                    parameters.forEach(uriBuilder::addParameter);

                    result.put(objectName, uriBuilder.build());
                }
            }
        } catch (SQLException | URISyntaxException e) {
            logger.warn("Error load URI {}", objectNameList, e);
        }

        return result;
    }

    private static class MfkServer {

        private final String name;
        private final String path;
        private final String scheme;
        private final String host;
        private final int port;

        public MfkServer(String name, String path, String scheme, String host, int port) {
            this.name = name;
            this.path = path;
            this.scheme = scheme;
            this.host = host;
            this.port = port;
        }
    }
}
