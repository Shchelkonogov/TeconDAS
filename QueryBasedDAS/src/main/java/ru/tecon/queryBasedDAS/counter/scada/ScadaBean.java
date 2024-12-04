package ru.tecon.queryBasedDAS.counter.scada;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 12.11.2024
 */
@Stateless(name = "scada", mappedName = "ejb/scada")
public class ScadaBean {

    private static final Pattern PATTERN_ID = Pattern.compile(".* \\((?<id>\\d+)\\)$");

    @Inject
    private Logger logger;

    @Inject
    private ObjectMapper om;

    public List<String> getObjects(String scheme, String host, int port) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            URI build = new URIBuilder()
                    .setScheme(scheme)
                    .setHost(host)
                    .setPort(port)
                    .setPathSegments(List.of("api", "objects"))
                    .build();

            HttpGet httpGet = new HttpGet(build);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    List<ScadaObject> objects = om.readValue(EntityUtils.toString(response.getEntity()), new TypeReference<List<ScadaObject>>() {});
                    return objects.stream().map(value -> value.objectName + " (" + value.id + ")").collect(Collectors.toList());
                } else {
                    logger.warn("Error request objects. Error code {}", response.getStatusLine().getStatusCode());
                }
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error request objects", e);
        }
        return List.of();
    }

    public Set<Config> getConfig(String object, String scheme, String host, int port) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Matcher m = PATTERN_ID.matcher(object);
            if (m.find()) {
                String id = m.group("id");

                URI build = new URIBuilder()
                        .setScheme(scheme)
                        .setHost(host)
                        .setPort(port)
                        .setPathSegments(List.of("api", "objectParams", id))
                        .build();

                HttpGet httpGet = new HttpGet(build);

                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        ScadaParameters parameters = om.readValue(EntityUtils.toString(response.getEntity()), new TypeReference<ScadaParameters>() {});

                        return parameters.parameters.stream()
                                .map(value -> new Config(value.paramName, String.valueOf(value.id)))
                                .collect(Collectors.toSet());
                    } else {
                        logger.warn("Error request objects. Error code {}", response.getStatusLine().getStatusCode());
                    }
                }
            } else {
                logger.warn("Error parse id {}", object);
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error request objects", e);
        }
        return Set.of();
    }

    public void loadData(List<DataModel> params, String objectName, String scheme, String host, int port) {
        logger.info("start load data from scada for {}", objectName);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Matcher m = PATTERN_ID.matcher(objectName);
            if (m.find()) {
                String id = m.group("id");

                URI build = new URIBuilder()
                        .setScheme(scheme)
                        .setHost(host)
                        .setPort(port)
                        .setPathSegments(List.of("api", "histData"))
                        .build();

                HttpPost httpPost = new HttpPost(build);

                ScadaHistReq reqData = new ScadaHistReq();
                reqData.setId(Integer.parseInt(id));

                List<ScadaHistParamReq> reqParams = new ArrayList<>();

                reqData.setParameters(reqParams);

                for (DataModel item: params) {
                    if (item.getStartDateTime() == null) {
                        item.setStartDateTime(LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS));
                    }

                    ScadaHistParamReq paramReq = new ScadaHistParamReq();
                    paramReq.setId(Integer.parseInt(item.getParamSysInfo()));
                    paramReq.setStartDate(item.getStartDateTime());

                    reqParams.add(paramReq);
                }

                httpPost.setHeader("Content-type", "application/json");
                httpPost.setEntity(new StringEntity(om.writeValueAsString(reqData)));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        ScadaHistResp scadaHistResp = om.readValue(
                                EntityUtils.toString(response.getEntity()),
                                new TypeReference<ScadaHistResp>() {}
                        );

                        for (ScadaHistParamResp histParamResp: scadaHistResp.parameters) {
                            for (DataModel param: params) {
                                if (param.getParamSysInfo().equals(String.valueOf(histParamResp.id))) {
                                    for (ScadaHistParamValueResp value: histParamResp.values) {
                                        if (value.value.matches("\\d+,\\d+")) {
                                            param.addData(value.value.replace(",", "."), value.date);
                                        } else {
                                            param.addData(value.value, value.date);
                                        }
                                    }
                                }
                            }
                        }

                        params.removeIf(dataModel -> dataModel.getData().isEmpty());

                        logger.info("data from mfk loaded for {}", objectName);
                    } else {
                        logger.warn("Error request hist data. Error code {}", response.getStatusLine().getStatusCode());
                    }
                }
            } else {
                logger.warn("Error parse id {}", objectName);
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error load hist data", e);
        }
    }

    public void loadInstantData(List<DataModel> params, String objectName, String scheme, String host, int port) throws DasException {
        logger.info("start load instant data from mfk for {}", objectName);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Matcher m = PATTERN_ID.matcher(objectName);
            if (m.find()) {
                String id = m.group("id");

                URI build = new URIBuilder()
                        .setScheme(scheme)
                        .setHost(host)
                        .setPort(port)
                        .setPathSegments(List.of("api", "instantData"))
                        .build();

                HttpPost httpPost = new HttpPost(build);

                ScadaInstReq reqData = new ScadaInstReq();
                reqData.setId(Integer.parseInt(id));

                List<Integer> reqParams = new ArrayList<>();

                reqData.setParameters(reqParams);

                for (DataModel item: params) {
                    reqParams.add(Integer.parseInt(item.getParamSysInfo()));
                }

                httpPost.setHeader("Content-type", "application/json");
                httpPost.setEntity(new StringEntity(om.writeValueAsString(reqData)));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        ScadaHistResp scadaHistResp = om.readValue(
                                EntityUtils.toString(response.getEntity()),
                                new TypeReference<ScadaHistResp>() {}
                        );

                        for (ScadaHistParamResp histParamResp: scadaHistResp.parameters) {
                            for (DataModel param: params) {
                                if (param.getParamSysInfo().equals(String.valueOf(histParamResp.id))) {
                                    for (ScadaHistParamValueResp value: histParamResp.values) {
                                        if (value.value.matches("\\d+,\\d+")) {
                                            param.addData(value.value.replace(",", "."), value.date);
                                        } else {
                                            param.addData(value.value, value.date);
                                        }
                                    }
                                }
                            }
                        }

                        params.removeIf(dataModel -> dataModel.getData().isEmpty());

                        logger.info("instant data from mfk loaded for {}", objectName);
                    } else {
                        logger.warn("Error request inst data. Error code {}", response.getStatusLine().getStatusCode());
                    }
                }
            } else {
                logger.warn("Error parse id {}", objectName);
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error load inst data", e);
        }
    }

    private static class ScadaObject {

        private int id;
        private String objectName;

        public void setId(int id) {
            this.id = id;
        }

        public void setObjectName(String objectName) {
            this.objectName = objectName;
        }
    }

    private static class ScadaParameters {

        private int id;
        private String objectName;
        private List<Parameter> parameters;

        public void setId(int id) {
            this.id = id;
        }

        public void setObjectName(String objectName) {
            this.objectName = objectName;
        }

        public void setParameters(List<Parameter> parameters) {
            this.parameters = parameters;
        }
    }

    private static class Parameter {

        private int id;
        private String paramName;
        private String measure;

        public void setId(int id) {
            this.id = id;
        }

        public void setParamName(String paramName) {
            this.paramName = paramName;
        }

        public void setMeasure(String measure) {
            this.measure = measure;
        }
    }

    private static class ScadaHistReq {

        private int id;
        private List<ScadaHistParamReq> parameters;

        public void setId(int id) {
            this.id = id;
        }

        public void setParameters(List<ScadaHistParamReq> parameters) {
            this.parameters = parameters;
        }

        public int getId() {
            return id;
        }

        public List<ScadaHistParamReq> getParameters() {
            return parameters;
        }
    }

    private static class ScadaHistParamReq {

        private int id;
        private LocalDateTime startDate;

        public void setId(int id) {
            this.id = id;
        }

        public void setStartDate(LocalDateTime startDate) {
            this.startDate = startDate;
        }

        public int getId() {
            return id;
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }
    }

    private static class ScadaHistResp {

        private int id;
        private List<ScadaHistParamResp> parameters;

        public void setId(int id) {
            this.id = id;
        }

        public void setParameters(List<ScadaHistParamResp> parameters) {
            this.parameters = parameters;
        }
    }

    private static class ScadaHistParamResp {

        private int id;
        private List<ScadaHistParamValueResp> values;

        public void setId(int id) {
            this.id = id;
        }

        public void setValues(List<ScadaHistParamValueResp> values) {
            this.values = values;
        }
    }

    private static class ScadaHistParamValueResp {

        private String value;
        private LocalDateTime date;

        public void setValue(String value) {
            this.value = value;
        }

        public void setDate(LocalDateTime date) {
            this.date = date;
        }
    }

    private static class ScadaInstReq {

        private int id;
        private List<Integer> parameters;

        public void setId(int id) {
            this.id = id;
        }

        public void setParameters(List<Integer> parameters) {
            this.parameters = parameters;
        }

        public int getId() {
            return id;
        }

        public List<Integer> getParameters() {
            return parameters;
        }
    }
}
