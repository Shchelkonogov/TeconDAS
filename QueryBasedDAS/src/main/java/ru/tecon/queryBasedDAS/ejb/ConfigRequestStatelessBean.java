package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.UploadServiceEJBFactory;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.uploaderService.ejb.das.ConfigRequestRemote;
import ru.tecon.uploaderService.ejb.UploaderServiceRemote;
import ru.tecon.uploaderService.model.RequestData;

import javax.ejb.*;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 17.01.2024
 */
@Stateless(name = "configRequestBean", mappedName = "ejb/configRequestBean")
@Remote(ConfigRequestRemote.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConfigRequestStatelessBean implements ConfigRequestRemote {

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASSingletonBean bean;

    @Override
    public void accept(RequestData requestData) {
        logger.info("accept config load request for {}", requestData);
        loadConfig(requestData);
    }

    @Override
    @Asynchronous
    public void acceptAsync(RequestData requestData) {
        logger.info("accept async config load request for {}", requestData);
        loadConfig(requestData);
    }

    /**
     * Получение конфигурации счетчика и отправка ее на сервер загрузки данных
     *
     * @param requestData информация о счетчике и сервере загрузки данных
     */
    private void loadConfig(RequestData requestData) {
        try {
            Counter instance = (Counter) Class.forName(bean.getCounter(requestData.getCounter())).getDeclaredConstructor().newInstance();
            Set<String> config = instance.getConfig(requestData.getObjectName());

            logger.info("config for {} {} {}", requestData.getCounter(), requestData.getObjectName(), config);

            UploaderServiceRemote remote = UploadServiceEJBFactory.getUploadServiceRemote(requestData.getServerName());

            if (!config.isEmpty()) {
                int uploadCount = remote.uploadConfig(config, requestData.getObjectId(), requestData.getObjectName());

                if (uploadCount < 0) {
                    logger.warn("error upload config for {} {}", requestData.getCounter(), requestData.getObjectName());
                    remote.updateCommand(0, requestData.getRequestId(), requestData.getObjectId(),
                            "Error",
                            "Ошибка сервиса загрузки данных");
                } else {
                    logger.info("success upload config for {} {}", requestData.getCounter(), requestData.getObjectName());
                    remote.updateCommand(1, requestData.getRequestId(), requestData.getObjectId(),
                            requestData.getObjectName(),
                            "Получено " + uploadCount + " новых элементов по объекту '" + requestData.getObjectName() + "'.");
                }
            }
        } catch (ReflectiveOperationException e) {
            logger.warn("error load counter = {}", requestData.getCounter(), e);
        } catch (NamingException | IOException e) {
            logger.warn("remote service {} unavailable", requestData.getServerName(), e);
        }
    }
}
