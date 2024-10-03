package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterAsyncRequest;
import ru.tecon.uploaderService.ejb.UploaderServiceRemote;
import ru.tecon.uploaderService.ejb.das.RemoteRequest;
import ru.tecon.uploaderService.model.DataModel;
import ru.tecon.uploaderService.model.RequestData;

import javax.ejb.*;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 27.02.2024
 */
@Stateless(name = "asyncRequestBean", mappedName = "ejb/asyncRequestBean")
@Remote(RemoteRequest.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AsyncRequestStatelessBean implements RemoteRequest {

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASSingletonBean bean;

    @EJB
    private RemoteEJBFactory remoteEJBFactory;

    @Override
    public void accept(RequestData requestData) {
        logger.info("accept instant data load request for {}", requestData);
        loadInstantData(requestData);
    }

    @Override
    @Asynchronous
    public void acceptAsync(RequestData requestData) {
        logger.info("accept async instant data load request for {}", requestData);
        loadInstantData(requestData);
    }

    /**
     * Получение мгновенных данных и отправка их на сервер загрузки данных
     *
     * @param requestData информация о счетчике и сервере загрузки данных
     */
    private void loadInstantData(RequestData requestData) {
        // TODO в production проверить работоспособность мгновенных данных
        try {
            UploaderServiceRemote remote = remoteEJBFactory.getUploadServiceRemote(requestData.getServerName());
            List<DataModel> objectModel = remote.loadInstantObjectModel(requestData.getObjectId());

            logger.info("object model for {} {}", requestData.getObjectName(), objectModel);

            if ((objectModel != null) && !objectModel.isEmpty()) {
                try {
                    FtpCounterAsyncRequest counter = (FtpCounterAsyncRequest) Class.forName(bean.getCounter(requestData.getCounter())).getDeclaredConstructor().newInstance();

                    counter.loadInstantData(objectModel, requestData.getObjectName());
                } catch (ReflectiveOperationException e) {
                    logger.warn("error load counter = {}", requestData.getServerName(), e);
                    remote.updateCommand(0, requestData.getRequestId(), requestData.getObjectId(),
                            "Error",
                            "Ошибка сервиса");
                    return;
                } catch (DasException e) {
                    remote.updateCommand(0, requestData.getRequestId(), requestData.getObjectId(),
                            "Error",
                            e.getMessage());
                    return;
                }

                if (!objectModel.isEmpty()) {
                    logger.info("object model with data for {} model {} data size {}",
                            requestData.getObjectName(),
                            objectModel.stream().map(DataModel::getParamName).collect(Collectors.toList()),
                            objectModel.stream().map(dataModel -> dataModel.getData().size()).collect(Collectors.toList()));

                    remote.uploadDataAsync(objectModel);
                    int count = remote.uploadInstantData(requestData.getRequestId(), objectModel);

                    if (count != -1) {
                        remote.updateCommand(1, requestData.getRequestId(), requestData.getObjectId(),
                                requestData.getObjectName(),
                                "Получено " + count + " мгновенных значений по объекту '" + requestData.getObjectName() + "'.");
                    } else {
                        remote.updateCommand(0, requestData.getRequestId(), requestData.getObjectId(),
                                "Error",
                                "Ошибка сервиса загрузки данных");
                    }
                } else {
                    remote.updateCommand(0, requestData.getRequestId(), requestData.getObjectId(),
                            "Error",
                            "Значения мгновенных параметров от прибора не получены");
                }
            } else {
                logger.warn("empty model for {}", requestData.getObjectName());
                remote.updateCommand(0, requestData.getRequestId(), requestData.getObjectId(),
                        "Error",
                        "Отсутствуют мгновенные параметры");
            }
        } catch (NamingException | DasException e) {
            logger.warn("remote service {} unavailable", requestData.getServerName(), e);
        }
    }
}
