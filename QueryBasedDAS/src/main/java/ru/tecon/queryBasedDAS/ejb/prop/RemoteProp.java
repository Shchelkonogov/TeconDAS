package ru.tecon.queryBasedDAS.ejb.prop;

import ru.tecon.queryBasedDAS.counter.Periodicity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 21.05.2024
 */
public class RemoteProp {

    private boolean enable;
    private boolean enableAlarm;
    private final Map<String, CounterProp> counters = new HashMap<>();

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public boolean isEnableAlarm() {
        return enableAlarm;
    }

    public void setEnableAlarm(boolean enableAlarm) {
        this.enableAlarm = enableAlarm;
    }

    public void addCounter(String key) {
        counters.put(key, new CounterProp());
    }

    public List<String> getCounters(Periodicity periodicity) {
        return counters.entrySet().stream()
                .filter(entry -> entry.getValue().periodicity == periodicity)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
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

    public static class CounterProp {

        private Periodicity periodicity = Periodicity.DISABLED;
        private int concurrencyDepth = 1;
        private int concurrencyAlarmDepth = 1;

        private CounterProp() {
        }

        public Periodicity getPeriodicity() {
            return periodicity;
        }

        public void setPeriodicity(Periodicity periodicity) {
            this.periodicity = periodicity;
        }

        public int getConcurrencyDepth() {
            return concurrencyDepth;
        }

        public void setConcurrencyDepth(int concurrencyDepth) {
            this.concurrencyDepth = concurrencyDepth;
        }

        public int getConcurrencyAlarmDepth() {
            return concurrencyAlarmDepth;
        }

        public void setConcurrencyAlarmDepth(int concurrencyAlarmDepth) {
            this.concurrencyAlarmDepth = concurrencyAlarmDepth;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", CounterProp.class.getSimpleName() + "[", "]")
                    .add("periodicity=" + periodicity)
                    .add("concurrencyDepth=" + concurrencyDepth)
                    .add("concurrencyAlarmDepth=" + concurrencyAlarmDepth)
                    .toString();
        }
    }
}
