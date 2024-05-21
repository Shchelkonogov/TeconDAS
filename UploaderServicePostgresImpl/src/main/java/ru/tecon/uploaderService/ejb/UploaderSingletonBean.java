package ru.tecon.uploaderService.ejb;

import ru.tecon.uploaderService.PropertiesLoader;
import ru.tecon.uploaderService.ejb.das.ListenerType;
import ru.tecon.uploaderService.model.Listener;

import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
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

    private final Map<ListenerKey, Listener> remoteListeners = new HashMap<>();

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
    public Set<Listener> getRemoteListeners(ListenerType type, String serverName) {
        return remoteListeners.entrySet()
                .stream()
                .filter(entry -> (entry.getKey().type == type) && entry.getValue().getCounterNameSet().contains(serverName))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
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
                            entry -> entry.getKey().dasName + "_" + entry.getKey().type,
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
        return remoteListeners.putIfAbsent(new ListenerKey(dasName, listener.getType()), listener);
    }

    /**
     * Проверка, существует ли заданный слушатель в системе
     *
     * @param dasName имя системы сбора данных
     * @return true, если уже существует
     */
    public boolean containsListener(String dasName, ListenerType type) {
        return remoteListeners.containsKey(new ListenerKey(dasName, type));
    }

    /**
     * Проверка, существует ли заданный слушатель в системе
     *
     * @return true, если уже существует
     */
    public boolean containsListener(String dasName) {
        return remoteListeners.keySet().stream().anyMatch(k -> k.dasName.equals(dasName));
    }

    /**
     * Удаление слушателя
     *
     * @param dasName имя системы сбора данных
     * @return коллекцию удаленных счетчиков
     */
    public Set<String> removeListener(String dasName, ListenerType type) {
        Listener remove = remoteListeners.remove(new ListenerKey(dasName, type));
        if (remove != null) {
            Set<String> collect = remoteListeners.entrySet().stream()
                    .filter(entry -> entry.getKey().dasName.equals(dasName))
                    .map(entry -> entry.getValue().getCounterNameSet())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());

            remove.getCounterNameSet().removeAll(collect);
            return remove.getCounterNameSet();
        }
        return Set.of();
    }

    /**
     * Получение системных свойств
     *
     * @return системные свойства
     */
    public Properties getProperties() {
        return properties;
    }

    private static class ListenerKey {

        private final String dasName;
        private final ListenerType type;

        private ListenerKey(String dasName, ListenerType type) {
            this.dasName = dasName;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListenerKey that = (ListenerKey) o;
            return dasName.equals(that.dasName) && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dasName, type);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ListenerKey.class.getSimpleName() + "[", "]")
                    .add("dasName='" + dasName + "'")
                    .add("type=" + type)
                    .toString();
        }
    }
}
