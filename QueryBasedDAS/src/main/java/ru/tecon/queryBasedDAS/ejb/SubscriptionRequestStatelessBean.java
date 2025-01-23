package ru.tecon.queryBasedDAS.ejb;

import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterSubscribe;
import ru.tecon.uploaderService.ejb.das.RemoteRequest;
import ru.tecon.uploaderService.model.RequestData;

import javax.ejb.*;
import javax.inject.Inject;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 21.01.2025
 */
@Stateless(name = "subscriptionRequestBean", mappedName = "ejb/subscriptionRequestBean")
@Remote(RemoteRequest.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class SubscriptionRequestStatelessBean implements RemoteRequest {

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASSingletonBean bean;

    @Override
    public void accept(RequestData requestData) {
        logger.info("accept subscript request for {}", requestData);
        subscript(requestData);
    }

    @Override
    @Asynchronous
    public void acceptAsync(RequestData requestData) {
        logger.info("accept async subscript request for {}", requestData);
        subscript(requestData);
    }

    private void subscript(RequestData requestData) {
        if (requestData.getProp().containsKey("sub")) {
            Set<String> objectRemoteSub = bean.getObjectRemoteSub(requestData.getObjectName());
            switch (requestData.getProp().get("sub")) {
                case "true":
                    if (objectRemoteSub.isEmpty()
                            && bean.counterSupportSubscriptionNameSet().contains(requestData.getCounter())) {
                        try {
                            CounterSubscribe cl = (CounterSubscribe) Class.forName(bean.getCounter(requestData.getCounter())).getDeclaredConstructor().newInstance();

                            cl.subscribe(requestData.getObjectName());

                            bean.putSubObject(requestData.getServerName(), requestData.getObjectName());
                        } catch (ReflectiveOperationException e) {
                            logger.warn("error load counter = {}", requestData.getCounter(), e);
                        } catch (DasException e) {
                            logger.warn("error subscribe object = {}", requestData.getObjectName(), e);
                        }
                    }

                    if (!objectRemoteSub.isEmpty()
                            && !objectRemoteSub.contains(requestData.getServerName())
                            && bean.counterSupportSubscriptionNameSet().contains(requestData.getCounter())) {
                        bean.putSubObject(requestData.getServerName(), requestData.getObjectName());
                    }
                    break;
                case "false":
                    if (objectRemoteSub.contains(requestData.getServerName())
                            && bean.counterSupportSubscriptionNameSet().contains(requestData.getCounter())) {
                        if (objectRemoteSub.size() == 1) {
                            try {
                                CounterSubscribe cl = (CounterSubscribe) Class.forName(bean.getCounter(requestData.getCounter())).getDeclaredConstructor().newInstance();

                                cl.unsubscribe(requestData.getObjectName());
                            } catch (ReflectiveOperationException e) {
                                logger.warn("error load counter = {}", requestData.getCounter(), e);
                            } catch (DasException e) {
                                logger.warn("error unsubscribe object = {}", requestData.getObjectName(), e);
                            }
                        }

                        bean.removeSubObject(requestData.getServerName(), requestData.getObjectName());
                    }
                    break;
            }
        }
    }
}
