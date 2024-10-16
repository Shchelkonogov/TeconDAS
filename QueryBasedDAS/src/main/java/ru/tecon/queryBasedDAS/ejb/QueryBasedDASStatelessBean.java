package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.*;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterAlarm;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterExtension;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.statistic.StatKey;
import ru.tecon.queryBasedDAS.counter.statistic.WebConsole;
import ru.tecon.uploaderService.ejb.UploaderServiceRemote;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;
import ru.tecon.uploaderService.model.SubscribedObject;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 10.01.2024
 */
@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class QueryBasedDASStatelessBean {

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASSingletonBean bean;

    @EJB
    private RemoteEJBFactory remoteEJBFactory;

    @Resource(mappedName = "concurrent/das")
    private ManagedExecutorService executorService;

    /**
     * Получение всех объектов счетчика
     *
     * @param counterName имя счетчика
     * @return объекты счетчика
     */
    public Map<String, List<String>> getCounterObjects(String counterName) {
        try {
            Counter instance = (Counter) Class.forName(bean.getCounter(counterName)).getDeclaredConstructor().newInstance();
            List<String> objects = instance.getCounterInfo().getObjects();
            Map<String, List<String>> counterObjects = Map.of(counterName, objects);

            logger.info("objects = {}", counterObjects);

            return counterObjects;
        } catch (ReflectiveOperationException e) {
            logger.warn("error load counter = {}", counterName, e);
        }
        return Map.of();
    }

    /**
     * Выгрузка объектов счетчиков на сервер загрузки данных
     *
     * @param serverName имя сервера загрузки данных
     * @param counterObjects объекты счетчиков
     * @return true, если успешная загрузка данных
     */
    public boolean uploadCounterObjects(String serverName, Map<String, List<String>> counterObjects) {
        try {
            remoteEJBFactory.getUploadServiceRemote(serverName).uploadObjects(counterObjects);

            // Добавление статистики
            for (Map.Entry<String, List<String>> entry: counterObjects.entrySet()) {
                WebConsole counterWebConsole = bean.getCounterWebConsole(entry.getKey());
                if (counterWebConsole != null) {
                    for (String counterName: entry.getValue()) {
                        counterWebConsole.getStatistic().computeIfAbsent(
                                new StatKey(serverName, counterName),
                                statKey -> StatData.builder(serverName, counterName, entry.getKey()).build()
                        );
                    }
                }
            }
            return true;
        } catch (NamingException | DasException e) {
            logger.warn("remote service {} unavailable", serverName, e);
            return false;
        }
    }

    /**
     * Выгрузка объектов счетчиков на все сервера загрузки данных
     */
    @Asynchronous
    public void uploadCounterObjects() {
        Map<String, List<String>> counterObjects = new HashMap<>();
        for (String serverName: bean.getRemotes().keySet()) {
            for (String counter: bean.counterNameSet(serverName)) {
                if (!counterObjects.containsKey(counter)) {
                    counterObjects.putAll(getCounterObjects(counter));
                }
                uploadCounterObjects(serverName, Map.of(counter, counterObjects.get(counter)));
            }
        }
        logger.info("finish upload counter objects");
    }

    /**
     * Загрузка конфигурации в базу по имени объекта счетчика.
     * Данная операция рискованная, т.к. происходит поиск объекта по имени в базе
     *
     * @param counterName название счетчика
     * @param counterObjectName название объекта счетчика
     * @param remoteServer удаленный сервер
     * @param config конфигурация
     */
    public void tryUploadConfigByCounterName(String counterName, String counterObjectName,
                                             String remoteServer, Set<Config> config) {
        try {
            UploaderServiceRemote remote = remoteEJBFactory.getUploadServiceRemote(remoteServer);

            String counterObjectId = remote.getCounterObjectId(counterName, counterObjectName);
            if (counterObjectId != null) {
                remote.uploadConfig(config, counterObjectId, counterObjectName);
            }

        } catch (NamingException | DasException e) {
            logger.warn("remote service {} unavailable", remoteServer, e);
        }
    }

    /**
     * Получение мгновенной конфигурации.
     * Данная операция рискованная, т.к. происходит поиск объекта по имени в базе
     *
     * @param counterName название счетчика
     * @param counterObjectName название объекта счетчика
     * @param remoteServer удаленный сервер
     * @return список мгновенных параметров
     */
    public List<DataModel> tryGetAsyncModelByCounterName(String counterName, String counterObjectName,
                                             String remoteServer) {
        try {
            UploaderServiceRemote remote = remoteEJBFactory.getUploadServiceRemote(remoteServer);

            String counterObjectId = remote.getCounterObjectId(counterName, counterObjectName);

            return remote.loadInstantObjectModel(counterObjectId);
        } catch (NamingException | DasException e) {
            logger.warn("remote service {} unavailable", remoteServer, e);
        }
        return new ArrayList<>();
    }

    /**
     * Очистка исторических файлов
     */
    @Asynchronous
    public void clearObjects() {
        Set<String> counterNameSet = bean.counterSupportRemoveHistoryNameSet();
        for (String counterName: counterNameSet) {
            try {
                FtpCounterExtension ftpCounter = (FtpCounterExtension) Class.forName(bean.getCounter(counterName)).getDeclaredConstructor().newInstance();
                ftpCounter.clearHistoricalFiles();
            } catch (ReflectiveOperationException e) {
                logger.warn("error load counter = {}", counterName, e);
            }
        }

        counterNameSet = bean.counterSupportAlarmNameSet();
        for (String counterName: counterNameSet) {
            try {
                FtpCounterAlarm ftpCounter = (FtpCounterAlarm) Class.forName(bean.getCounter(counterName)).getDeclaredConstructor().newInstance();
                ftpCounter.clearAlarms();
            } catch (ReflectiveOperationException e) {
                logger.warn("error load counter = {}", counterName, e);
            }
        }

        logger.info("finish clear counter objects");
    }

    /**
     * Загрузка alarm
     */
    public void loadAlarms() {
        Set<String> counterNameSet = bean.counterSupportAlarmNameSet();

        if (!counterNameSet.isEmpty()) {
            for (String uploadServerName: bean.getRemotes().keySet()) {
                if (!bean.getRemoteProp(uploadServerName).isEnableAlarm()) {
                    logger.info("alarms disabled for {}", uploadServerName);
                    continue;
                }

                try {
                    UploaderServiceRemote uploadServiceRemote = remoteEJBFactory.getUploadServiceRemote(uploadServerName);

                    List<SubscribedObject> objects = uploadServiceRemote.getSubscribedObjects(counterNameSet);

                    if ((objects != null) && !objects.isEmpty()) {
                        Map<String, Pair<String, Integer>> concurrencyAlarmDepthMap = objects.stream()
                                .map(SubscribedObject::getServerName)
                                .distinct()
                                .collect(Collectors.toMap(
                                        k -> k,
                                        v -> Pair.of(v, bean.getCounterProp(uploadServerName, v).getConcurrencyAlarmDepth())));

                        Map<Pair<String, Integer>, List<SubscribedObject>> collect = objects.stream().collect(Collectors.groupingBy(subscribedObject -> concurrencyAlarmDepthMap.get(subscribedObject.getServerName())));

                        for (Map.Entry<Pair<String, Integer>, List<SubscribedObject>> entry: collect.entrySet()) {
                            int currentPartCount = entry.getKey().second;
                            int chunk = entry.getValue().size() / currentPartCount;
                            int mod = entry.getValue().size() % currentPartCount;
                            for (int i = 0; i < entry.getValue().size(); i += chunk) {
                                int increment = 0;
                                if (mod > 0) {
                                    increment = 1;
                                    mod--;
                                }
                                List<SubscribedObject> subList = entry.getValue().subList(i, i + chunk + increment);

                                executorService.submit(() -> initReadAlarmFiles(subList, uploadServerName));
                                i += increment;
                            }
                        }
                    } else {
                        logger.warn("no subscribed objects for {}", uploadServerName);
                    }
                } catch (NamingException e) {
                    logger.warn("remote service {} unavailable", uploadServerName, e);
                } catch (DasException e) {
                    logger.warn("loadAlarms error {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Загрузка alarm
     *
     * @param objects список объектов для загрузки данных
     * @param uploadServerName имя удаленного сервера для загрузки данных
     */
    private void initReadAlarmFiles(List<SubscribedObject> objects, String uploadServerName) {
        logger.info("start load alarm data for {}", objects.stream().map(SubscribedObject::getObjectName).collect(Collectors.toList()));
        long startTime = System.currentTimeMillis();
        LocalDateTime startDateTime = LocalDateTime.now();
        try {
            UploaderServiceRemote uploadServiceRemote = remoteEJBFactory.getUploadServiceRemote(uploadServerName);

            Map<String, FtpCounterAlarm> loadedCounters = new HashMap<>();

            for (SubscribedObject object: objects) {
                List<DataModel> objectModel = uploadServiceRemote.loadObjectModelWithStartTimes(object.getId());

                logger.info("object model for {} {}", object.getObjectName(), objectModel);

                if ((objectModel != null) && !objectModel.isEmpty()) {
                    try {
                        FtpCounterAlarm cl;
                        if (loadedCounters.containsKey(object.getServerName())) {
                            cl = loadedCounters.get(object.getServerName());
                        } else {
                            cl = (FtpCounterAlarm) Class.forName(bean.getCounter(object.getServerName())).getDeclaredConstructor().newInstance();
                            loadedCounters.put(object.getServerName(), cl);
                        }
                        cl.loadAlarms(objectModel, object.getObjectName());
                    } catch (ReflectiveOperationException e) {
                        logger.warn("error load counter = {}", object.getServerName(), e);
                    }

                    if (!objectModel.isEmpty()) {
                        logger.info("object model with data for {} model {} data size {}",
                                object.getObjectName(),
                                objectModel.stream().map(DataModel::getParamName).collect(Collectors.toList()),
                                objectModel.stream().map(dataModel -> dataModel.getData().size()).collect(Collectors.toList()));

                        uploadServiceRemote.uploadDataAsync(objectModel);
                    }
                } else {
                    logger.warn("empty model for {}", object);
                }
            }
        } catch (NamingException | DasException e) {
            logger.warn("remote service {} unavailable", uploadServerName, e);
        }
        logger.info("finished load alarm data started at {} in {} ms for {}",
                startDateTime,
                (System.currentTimeMillis() - startTime),
                objects.stream().map(SubscribedObject::getObjectName).collect(Collectors.toList()));
    }

    /**
     * Загрузка исторических данных
     *
     * @param periodicity тип опроса счетчиков, если передать null, то будет загрузка по всем типам.
     */
    public void loadHistoricalData(Periodicity periodicity) {
        for (String uploadServerName: bean.getRemotes().keySet()) {
            try {
                UploaderServiceRemote uploadServiceRemote = remoteEJBFactory.getUploadServiceRemote(uploadServerName);

                Set<String> counterNameSet;
                if (periodicity == null) {
                    counterNameSet = bean.counterNameSet(uploadServerName);
                } else {
                    counterNameSet = bean.counterNameSet(uploadServerName, periodicity);
                }

                if (counterNameSet.isEmpty()) {
                    logger.warn("no counters for remote {} and periodicity {}", uploadServerName, periodicity);
                    continue;
                }

                List<SubscribedObject> objects = uploadServiceRemote.getSubscribedObjects(counterNameSet);

                if ((objects != null) && !objects.isEmpty()) {
                    Map<String, Pair<String, Integer>> concurrencyDepthMap = objects.stream()
                            .map(SubscribedObject::getServerName)
                            .distinct()
                            .collect(Collectors.toMap(
                                    k -> k,
                                    v -> Pair.of(v, bean.getCounterProp(uploadServerName, v).getConcurrencyDepth())));

                    Map<Pair<String, Integer>, List<SubscribedObject>> collect = objects.stream().collect(Collectors.groupingBy(subscribedObject -> concurrencyDepthMap.get(subscribedObject.getServerName())));

                    for (Map.Entry<Pair<String, Integer>, List<SubscribedObject>> entry: collect.entrySet()) {
                        int currentPartCount = entry.getKey().second;
                        int chunk = entry.getValue().size() / currentPartCount;
                        int mod = entry.getValue().size() % currentPartCount;
                        for (int i = 0; i < entry.getValue().size(); i += chunk) {
                            int increment = 0;
                            if (mod > 0) {
                                increment = 1;
                                mod--;
                            }
                            List<SubscribedObject> subList = entry.getValue().subList(i, i + chunk + increment);

                            executorService.submit(() -> initReadHistoricalFiles(subList, uploadServerName));
                            i += increment;
                        }
                    }
                } else {
                    logger.warn("no subscribed objects for {}", uploadServerName);
                }
            } catch (NamingException e) {
                logger.warn("remote service {} unavailable", uploadServerName, e);
            } catch (DasException e) {
                logger.warn("loadHistoricalData error {}", e.getMessage());
            }
        }
    }

    /**
     * Асинхронная загрузка исторических данных
     *
     * @param objects список объектов для загрузки данных
     * @param uploadServerName имя удаленного сервера для загрузки данных
     */
    private void initReadHistoricalFiles(List<SubscribedObject> objects, String uploadServerName) {
        logger.info("start load historical data for {}", objects.stream().map(SubscribedObject::getObjectName).collect(Collectors.toList()));
        long startTime = System.currentTimeMillis();
        LocalDateTime startDateTime = LocalDateTime.now();
        try {
            UploaderServiceRemote uploadServiceRemote = remoteEJBFactory.getUploadServiceRemote(uploadServerName);

            Map<String, Counter> loadedCounters = new HashMap<>();

            for (SubscribedObject object: objects) {
                StatData.Builder builder = StatData.builder(uploadServerName, object.getObjectName(), object.getServerName())
                        .startRequestTime(LocalDateTime.now());

                List<DataModel> objectModel = uploadServiceRemote.loadObjectModelWithStartTimes(object.getId());

                logger.info("object model for {} {}", object.getObjectName(), objectModel);

                if ((objectModel != null) && !objectModel.isEmpty()) {
                    try {
                        Counter cl;
                        if (loadedCounters.containsKey(object.getServerName())) {
                            cl = loadedCounters.get(object.getServerName());
                        } else {
                            cl = (Counter) Class.forName(bean.getCounter(object.getServerName())).getDeclaredConstructor().newInstance();
                            loadedCounters.put(object.getServerName(), cl);
                        }

                        // Добавление статистики
                        String objectNames = objectModel.stream()
                                .map(dataModel -> String.valueOf(dataModel.getObjectName()))
                                .distinct()
                                .collect(Collectors.joining(", "));
                        builder.objectName(objectNames);

                        objectModel.forEach(dataModel -> builder.addRequestedValue(dataModel.getParamName(), dataModel.getStartDateTime()));

                        cl.loadData(objectModel, object.getObjectName());
                    } catch (ReflectiveOperationException e) {
                        logger.warn("error load counter = {}", object.getServerName(), e);
                    }

                    if (!objectModel.isEmpty()) {
                        logger.info("object model with data for {} model {} data size {}",
                                object.getObjectName(),
                                objectModel.stream().map(DataModel::getParamName).collect(Collectors.toList()),
                                objectModel.stream().map(dataModel -> dataModel.getData().size()).collect(Collectors.toList()));

                        uploadServiceRemote.uploadDataWithModifyAsync(objectModel);

                        // Добавление статистики
                        builder.lastValuesUploadTime(LocalDateTime.now());
                        objectModel.forEach(dataModel -> {
                            if (dataModel.getData() instanceof TreeSet) {
                                TreeSet<DataModel.ValueModel> data = (TreeSet<DataModel.ValueModel>) dataModel.getData();
                                builder.addLastValue(dataModel.getParamName(), data.last().getValue(), data.last().getDateTime());
                            }
                        });
                    }
                } else {
                    logger.warn("empty model for {}", object);
                }

                builder.endRequestTime(LocalDateTime.now());

                WebConsole counterWebConsole = bean.getCounterWebConsole(object.getServerName());
                if (counterWebConsole != null) {
                    counterWebConsole.merge(new StatKey(uploadServerName, object.getObjectName()), builder.build());
                }
            }
        } catch (NamingException | DasException e) {
            logger.warn("remote service {} unavailable", uploadServerName, e);
        }
        logger.info("finished load historical data started at {} in {} ms for {}",
                startDateTime,
                (System.currentTimeMillis() - startTime),
                objects.stream().map(SubscribedObject::getObjectName).collect(Collectors.toList()));
    }

    private static class Pair<K, V> {

        private final K first;
        private final V second;

        private Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        public static <K, V> Pair<K, V> of(K k, V v) {
            return new Pair<>(k, v);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }
}
