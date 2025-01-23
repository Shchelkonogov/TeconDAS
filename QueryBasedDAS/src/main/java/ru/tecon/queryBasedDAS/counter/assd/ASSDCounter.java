package ru.tecon.queryBasedDAS.counter.assd;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.CounterSubscribe;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import java.util.List;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 20.12.2024
 */
public class ASSDCounter implements Counter, CounterSubscribe {

    private final ASSDCounterInfo info = ASSDCounterInfo.getInstance();

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
    public void subscribe(String objectName) throws DasException {
        info.getBean().subscribe(objectName);
    }

    @Override
    public void unsubscribe(String objectName) throws DasException {
        info.getBean().unsubscribe(objectName);
    }

    @Override
    public String parseObjectName(String data) throws DasException {
        return info.getBean().parseObjectName(data);
    }

    @Override
    public void parseData(String data, List<DataModel> dataModels) {
        info.getBean().parseData(data, dataModels);
    }
}
