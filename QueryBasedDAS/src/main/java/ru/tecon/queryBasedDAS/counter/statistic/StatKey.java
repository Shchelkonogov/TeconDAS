package ru.tecon.queryBasedDAS.counter.statistic;

import java.io.Serializable;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Класс описывающий ключи статистики
 *
 * @author Maksim Shchelkonogov
 * 23.04.2024
 */
public class StatKey implements Serializable {

    private final String server;
    private final String counter;

    public StatKey(String server, String counter) {
        this.server = server;
        this.counter = counter;
    }

    public String getServer() {
        return server;
    }

    public String getCounter() {
        return counter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatKey statKey = (StatKey) o;
        return Objects.equals(server, statKey.server) && Objects.equals(counter, statKey.counter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, counter);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StatKey.class.getSimpleName() + "[", "]")
                .add("server='" + server + "'")
                .add("counter='" + counter + "'")
                .toString();
    }
}
