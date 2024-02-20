package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.PropertiesLoader;
import ru.tecon.queryBasedDAS.UploadServiceEJBFactory;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.uploaderService.ejb.UploaderServiceRemote;
import ru.tecon.uploaderService.model.SubscribedObject;
import ru.tecon.uploaderService.model.DataModel;

import javax.ejb.*;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
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
    private QueryBasedDASStatelessBean dasStatelessBean;

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
     * Получение всех объектов счетчиков
     *
     * @return объекты счетчика
     */
    public Map<String, List<String>> getCounterObjects() {
        Map<String, List<String>> result = new HashMap<>();
        for (String counter: bean.counterNameSet()) {
            result.putAll(getCounterObjects(counter));
        }
        return result;
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
            UploadServiceEJBFactory.getUploadServiceRemote(serverName).uploadObjects(counterObjects);
            return true;
        } catch (Exception e) {
            logger.warn("Remote service {} is unavailable", serverName);
            return false;
        }
    }

    /**
     * Выгрузка объектов счетчиков на все сервера загрузки данных
     *
     * @param counterObjects объекты счетчиков
     * @return список серверов, куда успешно загрузились данные
     */
    public List<String> uploadCounterObjects(Map<String, List<String>> counterObjects) {
        List<String> result = new ArrayList<>();

        try {
            Properties appProperties = PropertiesLoader.loadProperties("app.properties");
            String[] serverNames = appProperties.getProperty("uploadServerNames").split(" ");

            for (String serverName: serverNames) {
                boolean isUpload = uploadCounterObjects(serverName, counterObjects);
                if (isUpload) {
                    result.add(serverName);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error application parameters");
        }

        return result;
    }

    /**
     * Выгрузка объектов счетчиков на все сервера загрузки данных
     *
     * @return список серверов, куда успешно загрузились данные
     */
    public List<String> uploadCounterObjects() {
        Map<String, List<String>> counterObjects = getCounterObjects();
        return uploadCounterObjects(counterObjects);
    }

    /**
     * Загрузка исторических данных
     *
     * @param periodicity тип опроса счетчиков, если передать null, то будет загрузка по всем типам.
     */
    public void loadHistoricalData(Periodicity periodicity) {
        try {
            Properties properties = PropertiesLoader.loadProperties("app.properties");
            String[] uploadServerNames = properties.getProperty("uploadServerNames").split(" ");
            int partCount = Integer.parseInt(properties.getProperty("partCount"));

            for (String uploadServerName: uploadServerNames) {
                try {
                    UploaderServiceRemote uploadServiceRemote = UploadServiceEJBFactory.getUploadServiceRemote(uploadServerName);

                    Set<String> counterNameSet;
                    if (periodicity == null) {
                        counterNameSet = bean.counterNameSet();
                    } else {
                        counterNameSet = bean.counterNameSet(periodicity);
                    }

                    List<SubscribedObject> objects = uploadServiceRemote.getSubscribedObjects(counterNameSet);

                    if ((objects != null) && !objects.isEmpty()) {
                        int chunk = objects.size() / partCount;
                        int mod = objects.size() % partCount;
                        for (int i = 0; i < objects.size(); i += chunk) {
                            int increment = 0;
                            if (mod > 0) {
                                increment = 1;
                                mod--;
                            }
                            dasStatelessBean.initReadHistoricalFiles(objects.subList(i, i + chunk + increment), uploadServerName);
                            i += increment;
                        }
                    } else {
                        logger.warn("no subscribed objects for {}", uploadServerName);
                    }
                } catch (NamingException | IOException e) {
                    logger.warn("remote service {} unavailable", uploadServerName);
                }
            }
        } catch (IOException e) {
            logger.warn("error load properties");
        }
    }

    /**
     * Асинхронная загрузка исторических данных
     *
     * @param objects список объектов для загрузки данных
     * @param uploadServerName имя удаленного сервера для загрузки данных
     */
    @Asynchronous
    public void initReadHistoricalFiles(List<SubscribedObject> objects, String uploadServerName) {
        logger.info("start load historical data for {}", objects.stream().map(SubscribedObject::getObjectName).collect(Collectors.toList()));
        long startTime = System.currentTimeMillis();
        LocalDateTime startDateTime = LocalDateTime.now();
        try {
            UploaderServiceRemote uploadServiceRemote = UploadServiceEJBFactory.getUploadServiceRemote(uploadServerName);

            Map<String, Counter> loadedCounters = new HashMap<>();

            for (SubscribedObject object: objects) {
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
                        cl.loadData(objectModel, object.getObjectName());
                    } catch (ReflectiveOperationException e) {
                        logger.warn("error load counter = {}", object.getServerName(), e);
                    }

                    objectModel.removeIf(dataModel -> dataModel.getData().isEmpty());

                    logger.info("object model with data for {} model {} data size {}",
                            object.getObjectName(),
                            objectModel.stream().map(DataModel::getParamName).collect(Collectors.toList()),
                            objectModel.stream().map(dataModel -> dataModel.getData().size()).collect(Collectors.toList()));

                    if (!objectModel.isEmpty()) {
                        uploadServiceRemote.uploadDataAsync(objectModel);
                    }
                } else {
                    logger.warn("empty model for {}", object);
                }
            }
        } catch (IOException | NamingException e) {
            logger.warn("remote service {} unavailable", uploadServerName);
        }
        logger.info("finished load historical data started at {} in {} ms for {}",
                startDateTime,
                (System.currentTimeMillis() - startTime),
                objects.stream().map(SubscribedObject::getObjectName).collect(Collectors.toList()));
    }
}
