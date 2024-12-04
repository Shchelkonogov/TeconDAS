package ru.tecon.queryBasedDAS.counter.scada;

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
 * 07.11.2024
 */
public class ScadaCounter implements Counter, CounterAsyncRequest {

    private final ScadaCounterInfo info = ScadaCounterInfo.getInstance();

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<Config> getConfig(String object) {
        return info.getBean().getConfig(object, ScadaCounterInfo.SCHEME, ScadaCounterInfo.HOST, ScadaCounterInfo.PORT);
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        info.getBean().loadData(params, objectName, ScadaCounterInfo.SCHEME, ScadaCounterInfo.HOST, ScadaCounterInfo.PORT);
    }

    @Override
    public void loadInstantData(List<DataModel> params, String objectName) throws DasException {
        info.getBean().loadInstantData(params, objectName, ScadaCounterInfo.SCHEME, ScadaCounterInfo.HOST, ScadaCounterInfo.PORT);
    }
}
