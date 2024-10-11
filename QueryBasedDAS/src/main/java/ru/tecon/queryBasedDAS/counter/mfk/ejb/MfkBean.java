package ru.tecon.queryBasedDAS.counter.mfk.ejb;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
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

    private static final Pattern PATTERN_IPV4 = Pattern.compile("_(?<ip>((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?))_", Pattern.CASE_INSENSITIVE);

    private static final String SELECT_OBJECTS = "select concat(b.name, '_', a.name) " +
                                                    "from mfk_object a, mfk_server b " +
                                                        "where a.server_id = b.id";
    private static final String SELECT_CONFIG = "select name " +
                                                    "from mfk_config " +
                                                        "where server_id = ?";
    private static final String SELECT_DATA = "select value, date from mfk_data md " +
                                                "join mfk_config mc " +
                                                    "on mc.id = md.param_id " +
                                                "join public.mfk_server ms " +
                                                    "on ms.id = mc.server_id " +
                                                        "where ms.name = ? " +
                                                            "and mc.name = ? " +
                                                            "and date > ? " +
                                                        "order by date";
    private static final String SELECT_SERVER = "select id, scheme, host, port, path from mfk_server where name = ?";

    @Inject
    private Logger logger;

    @Inject
    private Gson json;

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

                // Загрузка мгновенной конфигурации
                Matcher m = PATTERN_IPV4.matcher(object);
                if (m.find()) {
                    String url = m.group("ip");

                    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                        ArrayList<String> path = new ArrayList<>(Arrays.asList(resServer.getString("path").split("/")));
                        path.removeIf(String::isEmpty);
                        path.add("api");
                        path.add("instantConfig");

                        URI build = new URIBuilder()
                                .setScheme(resServer.getString("scheme"))
                                .setHost(resServer.getString("host"))
                                .setPort(resServer.getInt("port"))
                                .setPathSegments(path)
                                .addParameter("url", url)
                                .build();

                        HttpGet httpGet = new HttpGet(build);

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
                    } catch (IOException | URISyntaxException e) {
                        logger.warn("Error request instance config", e);
                    }
                } else {
                    logger.warn("Error parse ip address {}", object);
                }
            }
        } catch (SQLException e) {
            logger.warn("Error load config for {}", object, e);
        }

        return result;
    }

    public void loadData(List<DataModel> params, String objectName) {
        logger.info("start load data from mfk for {}", objectName);
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_DATA)) {
            stm.setFetchSize(1000);

            for (DataModel item: params) {
                if (item.getStartDateTime() == null) {
                    item.setStartDateTime(LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS));
                }

                stm.setString(1, objectName.split("_")[0]);
                stm.setString(2, item.getParamName() + "::" + item.getParamSysInfo());
                stm.setTimestamp(3, Timestamp.valueOf(item.getStartDateTime()));

                ResultSet res = stm.executeQuery();
                while (res.next()) {
                    item.addData(res.getString(1), res.getTimestamp(2).toLocalDateTime());
                }
            }
        } catch (SQLException e) {
            logger.warn("error load data from mfk for {}", objectName, e);
        }

        params.removeIf(dataModel -> dataModel.getData().isEmpty());

        logger.info("data from mfk loaded for {}", objectName);
    }

    public void loadInstantData(List<DataModel> params, String objectName) throws DasException {
        logger.info("start load instant data from mfk for {}", objectName);
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_SERVER)) {
            stm.setString(1, objectName.split("_")[0]);
            ResultSet res = stm.executeQuery();
            if (res.next()) {
                Matcher m = PATTERN_IPV4.matcher(objectName);
                if (m.find()) {
                    String url = m.group("ip");

                    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                        ArrayList<String> path = new ArrayList<>(Arrays.asList(res.getString("path").split("/")));
                        path.removeIf(String::isEmpty);
                        path.add("api");
                        path.add("instantData");

                        URI build = new URIBuilder()
                                .setScheme(res.getString("scheme"))
                                .setHost(res.getString("host"))
                                .setPort(res.getInt("port"))
                                .setPathSegments(path)
                                .addParameter("url", url)
                                .build();

                        HttpPost httpPost = new HttpPost(build);

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
                    } catch (IOException | URISyntaxException e) {
                        logger.warn("Error request instance data", e);
                        throw new DasException("Ошибка обращения к контроллеру мфк " + objectName);
                    }
                } else {
                    logger.warn("Error parse ip address {}", objectName);
                    throw new DasException("Не разобран url прибора " + objectName);
                }
            }
        } catch (SQLException e) {
            logger.warn("Error load instant data for {}", objectName, e);
            throw new DasException("База данных недоступна");
        }
        logger.info("instant data from mfk loaded for {}", objectName);
    }
}
