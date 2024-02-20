package ru.tecon.queryBasedDAS.counter.ftp.model;

import java.util.StringJoiner;

/**
 * @author Maksim Shchelkonogov
 * 07.02.2024
 */
public final class CounterData {

    private final Object value;
    private int quality = 192;

    public CounterData(String value) {
        this.value = value;
    }

    public CounterData(String value, int quality) {
        this.value = value;
        this.quality = quality;
    }

    public CounterData(Number value) {
        this.value = value;
    }

    public CounterData(Number value, int quality) {
        this.value = value;
        this.quality = quality;
    }

    public String getValue() {
        return value.toString();
    }

    public int getQuality() {
        return quality;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CounterData.class.getSimpleName() + "[", "]")
                .add("value=" + value)
                .add("quality=" + quality)
                .toString();
    }
}
