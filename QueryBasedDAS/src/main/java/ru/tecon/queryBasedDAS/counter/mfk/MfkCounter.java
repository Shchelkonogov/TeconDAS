package ru.tecon.queryBasedDAS.counter.mfk;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.CounterAsyncRequest;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import java.util.List;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 03.10.2024
 */
public class MfkCounter implements Counter, CounterAsyncRequest {

    private final MfkInfo info = MfkInfo.getInstance();

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<Config> getConfig(String object) {
        return info.getBean().getConfig(object);
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        info.getBean().loadData(params, objectName);
    }

    @Override
    public void loadInstantData(List<DataModel> params, String objectName) throws DasException {
        info.getBean().loadInstantData(params, objectName);
    }

    public void resetTraffic(String objectName) {
        info.getBean().resetTraffic(objectName);
    }
}
