package ru.tecon.queryBasedDAS.counter;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.uploaderService.model.DataModel;

import java.util.List;

/**
 * @author Maksim Shchelkonogov
 * 21.01.2025
 */
public interface CounterSubscribe {

    void subscribe(String objectName) throws DasException;

    void unsubscribe(String objectName) throws DasException;

    String parseObjectName(String data) throws DasException;

    void parseData(String data, List<DataModel> dataModels);
}
