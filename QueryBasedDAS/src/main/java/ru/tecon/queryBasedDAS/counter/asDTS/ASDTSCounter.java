package ru.tecon.queryBasedDAS.counter.asDTS;

import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.uploaderService.model.DataModel;

import javax.naming.NamingException;
import java.util.List;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
public class ASDTSCounter implements Counter {

    private final ASDTSInfo info = ASDTSInfo.getInstance();

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<String> getConfig(String object) {
        return info.getBean().getConfig(object);
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        info.getBean().loadData(params, objectName);
    }
}
