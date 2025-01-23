package ru.tecon.queryBasedDAS.counter.assd;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import javax.ejb.Local;
import java.util.List;
import java.util.Set;

/**
 * @author Maksim Shchelkonogov
 * 20.12.2024
 */
@Local
public interface ASSDBeanLocal {

    String parseObjectName(String data) throws DasException;

    void parseData(String data, List<DataModel> dataModels);

    void subscribe(String object) throws DasException;

    void unsubscribe(String object) throws DasException;

    List<String> getObjects();

    Set<Config> getConfig(String object);

    void loadData(List<DataModel> params, String objectName);
}
