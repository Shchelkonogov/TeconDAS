package ru.tecon.queryBasedDAS.counter.statistic;

import ru.tecon.queryBasedDAS.DasException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * @author Maksim Shchelkonogov
 * 10.06.2024
 */
public class StatisticSerializer implements Serializable {

    private final Map<StatKey, StatData> statistic;

    private StatisticSerializer(Map<StatKey, StatData> statistic) {
        this.statistic = statistic;
    }

    public static void serialize(Map<StatKey, StatData> statistic, Path path) throws DasException {
        try (OutputStream os = Files.newOutputStream(path);
             ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(new StatisticSerializer(statistic));
        } catch (IOException e) {
            throw new DasException("serialize exception", e);
        }
    }

    public static Map<StatKey, StatData> deserialize(Path path) throws DasException {
        try (InputStream is = Files.newInputStream(path);
             ObjectInputStream ois = new ObjectInputStream(is)) {
            return ((StatisticSerializer) ois.readObject()).statistic;
        } catch (IOException | ClassNotFoundException e) {
            throw new DasException("deserialize exception", e);
        }
    }
}
