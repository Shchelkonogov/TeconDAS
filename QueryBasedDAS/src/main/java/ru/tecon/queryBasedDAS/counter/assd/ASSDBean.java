package ru.tecon.queryBasedDAS.counter.assd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
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
import ru.tecon.queryBasedDAS.HttpDeleteBody;
import ru.tecon.queryBasedDAS.counter.assd.prop.Json;
import ru.tecon.queryBasedDAS.counter.assd.prop.JsonType;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 20.12.2024
 */
@Stateless(name = "assd", mappedName = "ejb/assd")
@Local(ASSDBeanLocal.class)
public class ASSDBean implements ASSDBeanLocal {

    private static final Pattern PATTERN_MUID = Pattern.compile(".* \\((?<muid>\\d+)\\)$");

    @Inject
    private Logger logger;

    @Inject
    @Json(type = JsonType.RESPONSE)
    private ObjectMapper om;

    @Inject
    @Json(type = JsonType.REQUEST)
    private ObjectMapper omRequest;

    @Override
    public String parseObjectName(String data) throws DasException {
        try {
            ASSDObjectItem assdObjectItem = om.readValue(data, new TypeReference<ASSDObjectItem>() {});
            if ((assdObjectItem.address != null) && (assdObjectItem.muid != null)) {
                return ASSDCounterInfo.getInstance().getCounterUserName()
                        + "_" + assdObjectItem.address
                        + " (" + assdObjectItem.muid + ")";
            } else {
                throw new DasException("Error parse assd object name. Empty data");
            }
        } catch (JsonProcessingException e) {
            logger.warn("Error parse assd object name", e);
            throw new DasException("Error parse assd object name", e);
        }
    }

    @Override
    public void parseData(String data, List<DataModel> dataModels) {
        try {
            ASSDParamData assdParamData = om.readValue(data, new TypeReference<ASSDParamData>() {});

            logger.info("assd param data {}", assdParamData);

            Map<String, List<ASSDBean.HistDataValue>> histData = new HashMap<>();
            StringBuilder sb;
            for (ASSDBean.ASSDDevice device: assdParamData.devices) {
                for (ASSDBean.ASSDMeteringPoint meteringPoint: device.meteringPoints) {
                    for (ASSDBean.ASSDParam parameter: meteringPoint.parameters) {
                        sb = new StringBuilder();
                        sb.append(device.modelDevice)
                                .append(":")
                                .append(device.serialNumber)
                                .append(":")
                                .append(meteringPoint.zoneMeteringPoint)
                                .append(":")
                                .append(parameter.parName)
                                .append(":")
                                .append(parameter.measure)
                                .append(":")
                                .append(parameter.statAggr);
                        histData.computeIfAbsent(sb.toString(), k -> new ArrayList<>()).add(new ASSDBean.HistDataValue(parameter.value, parameter.timeStamp));
                    }
                }
            }

            for (DataModel param: dataModels) {
                if (histData.containsKey(param.getParamName())) {
                    for (ASSDBean.HistDataValue value: histData.get(param.getParamName())) {
                        param.addData(value.value, value.dateTime);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            logger.warn("Error parse assd param data", e);
        }

        dataModels.removeIf(dataModel -> dataModel.getData().isEmpty());
    }

    @Override
    public void subscribe(String object) throws DasException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Matcher m = PATTERN_MUID.matcher(object);
            if (m.find()) {
                String muid = m.group("muid");

                URI build = new URIBuilder()
                        .setScheme(ASSDCounterInfo.SCHEME)
                        .setHost(ASSDCounterInfo.HOST)
                        .setPort(ASSDCounterInfo.PORT)
                        .setPathSegments(List.of("api", "subscriptionAdd"))
                        .build();

                HttpPost httpPost = new HttpPost(build);

                httpPost.addHeader("Content-type", "application/json");
                httpPost.addHeader("Api-Key", ASSDCounterInfo.API_KEY);
                httpPost.addHeader("Client-Id", ASSDCounterInfo.CLIENT_ID);

                ASSDRequestHistData reqData = new ASSDRequestHistData();
                reqData.setMuid(Long.parseLong(muid));

                LocalDateTime minDateTime = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);

                reqData.setStartDate(minDateTime);

                httpPost.setEntity(new StringEntity(omRequest.writeValueAsString(reqData)));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (response.getStatusLine().getStatusCode() != 201) {
                        logger.warn("Error subscription. Error code {}", response.getStatusLine().getStatusCode());
                        try {
                            ResponseMessage message = omRequest.readValue(
                                    EntityUtils.toString(response.getEntity()),
                                    new TypeReference<ResponseMessage>() {}
                            );
                            logger.warn("Error message {}", message);
                            if (!message.message.equals("У вас уже есть активная подписка на этот объект. Чтобы оформить новую подписку, необходимо отменить текущую")) {
                                throw new DasException("Error subscription message " + message);
                            }
                        } catch (Exception ignore) {
                        }
                    }
                }
            } else {
                logger.warn("Error parse muid {}", object);
                throw new DasException("Error parse muid " + object);
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error subscription", e);
            throw new DasException("Error subscription", e);
        }
    }

    @Override
    public void unsubscribe(String object) throws DasException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Matcher m = PATTERN_MUID.matcher(object);
            if (m.find()) {
                String muid = m.group("muid");

                URI build = new URIBuilder()
                        .setScheme(ASSDCounterInfo.SCHEME)
                        .setHost(ASSDCounterInfo.HOST)
                        .setPort(ASSDCounterInfo.PORT)
                        .setPathSegments(List.of("api", "subscriptionAdd"))
                        .build();

                HttpPost httpPost = new HttpPost(build);

                httpPost.addHeader("Content-type", "application/json");
                httpPost.addHeader("Api-Key", ASSDCounterInfo.API_KEY);
                httpPost.addHeader("Client-Id", ASSDCounterInfo.CLIENT_ID);

                ASSDRequestHistData reqData = new ASSDRequestHistData();
                reqData.setMuid(Long.parseLong(muid));

                LocalDateTime minDateTime = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);

                reqData.setStartDate(minDateTime);

                httpPost.setEntity(new StringEntity(omRequest.writeValueAsString(reqData)));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    logger.info("unsubscription request code {}", response.getStatusLine().getStatusCode());
                    try {
                        ResponseMessage message = omRequest.readValue(
                                EntityUtils.toString(response.getEntity()),
                                new TypeReference<ResponseMessage>() {}
                        );
                        logger.info("unsubscription message {}", message);
                        if (message.message.equals("У вас уже есть активная подписка на этот объект. Чтобы оформить новую подписку, необходимо отменить текущую")) {
                            removeSubscription(muid, message.subscription_id);
                        } else {
                            throw new DasException("Error unsubscription message " + message);
                        }
                    } catch (Exception ignore) {
                    }
                }
            } else {
                logger.warn("Error parse muid {}", object);
                throw new DasException("Error parse muid " + object);
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error unsubscription", e);
            throw new DasException("Error unsubscription", e);
        }
    }

    @Override
    public List<String> getObjects() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            URIBuilder builder = new URIBuilder()
                    .setScheme(ASSDCounterInfo.SCHEME)
                    .setHost(ASSDCounterInfo.HOST)
                    .setPort(ASSDCounterInfo.PORT)
                    .setPathSegments(List.of("api", "objects"))
                    .addParameter("offset", "0")
                    .addParameter("limit", "0");

            Set<String> result = new HashSet<>();
            loadObjects(result, builder, httpClient, 0, 500);
            return List.copyOf(result);
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error request objects", e);
        }
        return List.of();
    }

    private void loadObjects(Set<String> objects, URIBuilder builder, CloseableHttpClient httpClient, int offset, int limit) throws URISyntaxException, IOException {
        builder.setParameter("offset", String.valueOf(offset))
                .setParameter("limit", String.valueOf(limit));
        HttpGet httpGet = new HttpGet(builder.build());

        httpGet.addHeader("Api-Key", ASSDCounterInfo.API_KEY);
        httpGet.addHeader("Client-Id", ASSDCounterInfo.CLIENT_ID);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                ASSDObject assdObject = om.readValue(EntityUtils.toString(response.getEntity()), new TypeReference<ASSDObject>() {});
                objects.addAll(assdObject.items.stream().map(v -> ASSDCounterInfo.getInstance().getCounterUserName() + "_" + v.address + " (" + v.muid + ")").collect(Collectors.toSet()));

                if (!assdObject.items.isEmpty()) {
                    loadObjects(objects, builder, httpClient, offset + limit, limit);
                }
            } else {
                logger.warn("Error request objects. Error code {}", response.getStatusLine().getStatusCode());
            }
        }
    }

    @Override
    public Set<Config> getConfig(String object) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Matcher m = PATTERN_MUID.matcher(object);
            if (m.find()) {
                String muid = m.group("muid");

                URI build = new URIBuilder()
                        .setScheme(ASSDCounterInfo.SCHEME)
                        .setHost(ASSDCounterInfo.HOST)
                        .setPort(ASSDCounterInfo.PORT)
                        .setPathSegments(List.of("api", "getObjectParam"))
                        .addParameter("muid", muid)
                        .build();

                HttpGet httpGet = new HttpGet(build);

                httpGet.addHeader("Api-Key", ASSDCounterInfo.API_KEY);
                httpGet.addHeader("Client-Id", ASSDCounterInfo.CLIENT_ID);

                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        ASSDParams parameters = om.readValue(EntityUtils.toString(response.getEntity()), new TypeReference<ASSDParams>() {});

                        Set<Config> configs = new HashSet<>();
                        StringBuilder sb;

                        for (ASSDParamData item: parameters.items) {
                            for (ASSDDevice device: item.devices) {
                                for (ASSDMeteringPoint meteringPoint: device.meteringPoints) {
                                    for (ASSDParam parameter: meteringPoint.parameters) {
                                        sb = new StringBuilder();
                                        sb.append(device.modelDevice)
                                                .append(":")
                                                .append(device.serialNumber)
                                                .append(":")
                                                .append(meteringPoint.zoneMeteringPoint)
                                                .append(":")
                                                .append(parameter.parName)
                                                .append(":")
                                                .append(parameter.measure)
                                                .append(":")
                                                .append(parameter.statAggr);
                                        configs.add(new Config(sb.toString()));
                                    }
                                }
                            }
                        }

                        return configs;
                    } else {
                        logger.warn("Error request object params. Error code {}", response.getStatusLine().getStatusCode());
                    }
                }
            } else {
                logger.warn("Error parse muid {}", object);
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error request object params", e);
        }
        return Set.of();
    }

    public void loadData(List<DataModel> params, String objectName) {
        logger.info("start load data from assd for {}", objectName);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Matcher m = PATTERN_MUID.matcher(objectName);
            if (m.find()) {
                String muid = m.group("muid");

                URI build = new URIBuilder()
                        .setScheme(ASSDCounterInfo.SCHEME)
                        .setHost(ASSDCounterInfo.HOST)
                        .setPort(ASSDCounterInfo.PORT)
                        .setPathSegments(List.of("api", "subscriptionAdd"))
                        .build();

                HttpPost httpPost = new HttpPost(build);

                httpPost.addHeader("Content-type", "application/json");
                httpPost.addHeader("Api-Key", ASSDCounterInfo.API_KEY);
                httpPost.addHeader("Client-Id", ASSDCounterInfo.CLIENT_ID);

                ASSDRequestHistData reqData = new ASSDRequestHistData();
                reqData.setMuid(Long.parseLong(muid));

                LocalDateTime minDateTime = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
                for (DataModel item: params) {
                    if (item.getStartDateTime() == null) {
                        item.setStartDateTime(LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS));
                    }

                    if (item.getStartDateTime().isBefore(minDateTime)) {
                        minDateTime = item.getStartDateTime();
                    }
                }

                reqData.setStartDate(minDateTime);
                reqData.setEndDate(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS));

                httpPost.setEntity(new StringEntity(omRequest.writeValueAsString(reqData)));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        ASSDHistData assdHistResp = om.readValue(
                                EntityUtils.toString(response.getEntity()),
                                new TypeReference<ASSDHistData>() {}
                        );

                        Map<String, List<HistDataValue>> histData = new HashMap<>();
                        StringBuilder sb;
                        for (ASSDDevice device: assdHistResp.telemetry.devices) {
                            for (ASSDMeteringPoint meteringPoint: device.meteringPoints) {
                                for (ASSDParam parameter: meteringPoint.parameters) {
                                    sb = new StringBuilder();
                                    sb.append(device.modelDevice)
                                            .append(":")
                                            .append(device.serialNumber)
                                            .append(":")
                                            .append(meteringPoint.zoneMeteringPoint)
                                            .append(":")
                                            .append(parameter.parName)
                                            .append(":")
                                            .append(parameter.measure)
                                            .append(":")
                                            .append(parameter.statAggr);
                                    histData.computeIfAbsent(sb.toString(), k -> new ArrayList<>()).add(new HistDataValue(parameter.value, parameter.timeStamp));
                                }
                            }
                        }

                        for (DataModel param: params) {
                            if (histData.containsKey(param.getParamName())) {
                                for (HistDataValue value: histData.get(param.getParamName())) {
                                    param.addData(value.value, value.dateTime);
                                }
                            }
                        }

                        params.removeIf(dataModel -> dataModel.getData().isEmpty());

                        logger.info("data from assd loaded for {}", objectName);
                    } else {
                        logger.warn("Error request hist data. Error code {}", response.getStatusLine().getStatusCode());

                        boolean repeat = false;
                        try {
                            ResponseMessage message = omRequest.readValue(
                                    EntityUtils.toString(response.getEntity()),
                                    new TypeReference<ResponseMessage>() {}
                            );
                            logger.warn("Error message {}", message);

                            if (message.message.equals("У вас уже есть активная подписка на этот объект. Чтобы оформить новую подписку, необходимо отменить текущую")
                                    && removeSubscription(muid, message.subscription_id)) {
                                repeat = true;
                            }
                        } catch (Exception ignore) {
                        }

                        if (repeat) {
                            logger.info("Repeat load data for {}", objectName);
                            loadData(params, objectName);
                        }
                    }
                }
            } else {
                logger.warn("Error parse muid {}", objectName);
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error load hist data", e);
        }
    }

    private boolean removeSubscription(String muid, String subId) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            URI build = new URIBuilder()
                    .setScheme(ASSDCounterInfo.SCHEME)
                    .setHost(ASSDCounterInfo.HOST)
                    .setPort(ASSDCounterInfo.PORT)
                    .setPathSegments(List.of("api", "subscriptionRemove"))
                    .build();

            HttpDeleteBody httpDel = new HttpDeleteBody(build);

            httpDel.addHeader("Content-type", "application/json");
            httpDel.addHeader("Api-Key", ASSDCounterInfo.API_KEY);
            httpDel.addHeader("Client-Id", ASSDCounterInfo.CLIENT_ID);

            DeleteSubRequest reqData = new DeleteSubRequest(Long.parseLong(muid), Integer.parseInt(subId));

            httpDel.setEntity(new StringEntity(omRequest.writeValueAsString(List.of(reqData))));

            try (CloseableHttpResponse response = httpClient.execute(httpDel)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    ResponseMessage message = omRequest.readValue(
                            EntityUtils.toString(response.getEntity()),
                            new TypeReference<ResponseMessage>() {}
                    );

                    if (message.message.equals("Подписка отменена")) {
                        return true;
                    }
                } else {
                    logger.warn("Error remove subscription. Error code {}", response.getStatusLine().getStatusCode());
                }
            }
        } catch (IOException | URISyntaxException e) {
            logger.warn("Error remove subscription", e);
        }

        return false;
    }

    @Data
    private static class ASSDObject {
        private List<ASSDObjectItem> items;
        private int total;
        private int limit;
        private int offset;
    }

    @Data
    private static class ASSDObjectItem {
        private String address;
        private String muid;
    }

    @Data
    private static class ASSDParams {
        private List<ASSDParamData> items;
    }

    @Data
    private static class ASSDParamData {
        private String address;
        private String muid;
        private List<ASSDDevice> devices;
    }

    @Data
    private static class ASSDDevice {
        private String modelDevice;
        private String serialNumber;
        private List<ASSDMeteringPoint> meteringPoints;
    }

    @Data
    private static class ASSDMeteringPoint {
        private String zoneMeteringPoint;
        private List<ASSDParam> parameters;
    }

    @Data
    private static class ASSDParam {
        private String parName;
        private String value;
        private String measure;
        private String statAggr;
        private LocalDateTime timeStamp;
    }

    @Data
    private static class ASSDRequestHistData {
        private long muid;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @Data
    private static class ASSDHistData {
        private ASSDParamData telemetry;
    }

    @Data
    private static class HistDataValue {
        private final String value;
        private final LocalDateTime dateTime;
    }

    @Data
    private static class ResponseMessage {
        private String message;
        private String subscription_id;
    }

    @Data
    private static class DeleteSubRequest {
        private final long muid;
        private final int subSid;
    }
}
