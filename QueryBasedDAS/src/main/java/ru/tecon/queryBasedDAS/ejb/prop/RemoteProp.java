package ru.tecon.queryBasedDAS.ejb.prop;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.tecon.queryBasedDAS.counter.Periodicity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 21.05.2024
 */
public class RemoteProp {

    @Setter
    @Getter
    private boolean enable;
    @Setter
    @Getter
    private boolean enableAlarm;
    private final Map<String, CounterProp> counters = new HashMap<>();

    public void addCounter(String key) {
        counters.put(key, new CounterProp());
    }

    public List<String> getCounters(Periodicity periodicity) {
        return counters.entrySet().stream()
                .filter(entry -> entry.getValue().periodicity == periodicity)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<String> getCounters() {
        return new ArrayList<>(counters.keySet());
    }

    public CounterProp getCounterProp(String counterName) {
        return counters.get(counterName);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RemoteProp.class.getSimpleName() + "[", "]")
                .add("enable=" + enable)
                .add("counters=" + counters)
                .toString();
    }

    @Setter
    @Getter
    @ToString
    public static class CounterProp {

        private Periodicity periodicity = Periodicity.DISABLED;
        private int concurrencyDepth = 1;
        private int concurrencyAlarmDepth = 1;

        private CounterProp() {
        }
    }
}
