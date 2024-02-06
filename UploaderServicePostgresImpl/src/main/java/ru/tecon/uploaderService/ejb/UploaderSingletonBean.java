package ru.tecon.uploaderService.ejb;

import ru.tecon.uploaderService.PropertiesLoader;
import ru.tecon.uploaderService.model.Listener;

import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 16.01.2024
 */
@Singleton
@Startup
@LocalBean
public class UploaderSingletonBean {

    private final Map<String, Listener> remoteListeners = new HashMap<>();

    private Properties properties;

    @PostConstruct
    private void init() {
        // Загрузка свойств системы
        try {
            properties = PropertiesLoader.loadProperties("app.properties");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Получение удаленных слушателей
     *
     * @return удаленные слушатели
     */
    public Collection<Listener> getRemoteListeners() {
        return remoteListeners.values();
    }

    /**
     * Получение имен счетчиков удаленных слушателей
     *
     * @return Получение имен счетчиков удаленных слушателей
     */
    public Map<String, Set<String>> getDasListeners() {
        return remoteListeners.entrySet()
                .stream()
                .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getCounterNameSet()
                ));
    }

    /**
     * Добавить удаленного слушателя, если на такое имя еще не зарегистрировался слушатель
     *
     * @param dasName имя системы сбора данных
     * @param listener удаленный слушатель
     * @return null если слушателя не были или слушатель, если он есть
     */
    public Listener putListener(String dasName, Listener listener) {
        return remoteListeners.putIfAbsent(dasName, listener);
    }

    /**
     * Проверка, существует ли заданный слушатель в системе
     *
     * @param dasName имя системы сбора данных
     * @return true, если уже существует
     */
    public boolean containsListener(String dasName) {
        return remoteListeners.containsKey(dasName);
    }

    /**
     * Удаление слушателя
     *
     * @param dasName имя системы сбора данных
     * @return предыдущее значение или null
     */
    public Listener removeListener(String dasName) {
        return remoteListeners.remove(dasName);
    }

    /**
     * Получение системных свойств
     *
     * @return системные свойства
     */
    public Properties getProperties() {
        return properties;
    }
}
