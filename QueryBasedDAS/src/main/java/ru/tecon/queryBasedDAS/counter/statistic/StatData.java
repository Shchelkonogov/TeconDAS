package ru.tecon.queryBasedDAS.counter.statistic;

import org.jetbrains.annotations.NotNull;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Класс описывающий значения статистики
 *
 * @author Maksim Shchelkonogov
 * 18.04.2024
 */
public class StatData {

    private final String remoteName;
    private final String counterName;

    private String objectName;
    private LocalDateTime startRequestTime;
    private LocalDateTime endRequestTime;

    private List<RequestedValue> requestedValues = new ArrayList<>();
    private List<LastValue> lastValues = new ArrayList<>();

    private StatData(String remoteName, String counterName) {
        this.remoteName = remoteName;
        this.counterName = counterName;
    }

    public static Builder builder(String remoteName, String counterName) {
        return new Builder(remoteName, counterName);
    }

    public String getRemoteName() {
        return remoteName;
    }

    public String getCounterName() {
        return counterName;
    }

    public String getObjectName() {
        return objectName;
    }

    public LocalDateTime getStartRequestTime() {
        return startRequestTime;
    }

    public LocalDateTime getEndRequestTime() {
        return endRequestTime;
    }

    public String getRequestTime() {
        if (endRequestTime != null) {
            Duration between = Duration.between(startRequestTime, endRequestTime);
            return String.valueOf(between.toMillis());
        }
        return "";
    }

    public String getLastDataTimeString() {
        LocalDateTime dateTime = lastValues.stream()
                .map(LastValue::getDateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (dateTime != null) {
            return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        }
        return "";
    }

    public String getRequestedRange() {
        if (!requestedValues.isEmpty()) {
            Collections.sort(requestedValues);

            return parseDate(requestedValues.get(0).requestedDateTime) +
                    " - " +
                    parseDate(requestedValues.get(requestedValues.size() - 1).requestedDateTime);
        }
        return "";
    }

    private String parseDate(LocalDateTime dateTime) {
        String result = "Все известные";
        if (dateTime != null) {
            result = dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        }
        return result;
    }

    public List<LastValue> getLastValues() {
        return lastValues;
    }

    public List<RequestedValue> getRequestedValues() {
        return requestedValues;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StatData.class.getSimpleName() + "[", "]")
                .add("remoteName='" + remoteName + "'")
                .add("counterName='" + counterName + "'")
                .add("objectName='" + objectName + "'")
                .add("startRequestTime=" + startRequestTime)
                .add("endRequestTime=" + endRequestTime)
                .add("requestedValues=" + requestedValues)
                .add("lastValues=" + lastValues)
                .toString();
    }

    public static final class Builder {

        private final String remoteName;
        private final String counterName;
        private String objectName;
        private LocalDateTime startRequestTime;
        private LocalDateTime endRequestTime;

        private final List<RequestedValue> requestedValues = new ArrayList<>();
        private final List<LastValue> lastValues = new ArrayList<>();

        public Builder(String remoteName, String counterName) {
            this.remoteName = remoteName;
            this.counterName = counterName;
        }

        public Builder objectName(String objectName) {
            this.objectName = objectName;
            return this;
        }

        public Builder startRequestTime(LocalDateTime startRequestTime) {
            this.startRequestTime = startRequestTime;
            return this;
        }

        public Builder endRequestTime(LocalDateTime endRequestTime) {
            this.endRequestTime = endRequestTime;
            return this;
        }

        public Builder addRequestedValue(String paramName, LocalDateTime requestedDateTime) {
            requestedValues.add(new RequestedValue(paramName, requestedDateTime));
            return this;
        }

        public Builder addLastValue(String paramName, String value, LocalDateTime dateTime) {
            lastValues.add(new LastValue(paramName, value, dateTime));
            return this;
        }

        public StatData build() {
            StatData statData = new StatData(remoteName, counterName);
            statData.objectName = objectName;
            statData.startRequestTime = startRequestTime;
            statData.endRequestTime = endRequestTime;
            statData.requestedValues = requestedValues;
            statData.lastValues = lastValues;
            return statData;
        }
    }

    public static final class LastValue {

        private final String paramName;
        private final String value;
        private final LocalDateTime dateTime;

        private LastValue(String paramName, String value, LocalDateTime dateTime) {
            this.paramName = paramName;
            this.value = value;
            if (dateTime != null) {
                this.dateTime = dateTime.atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneOffset.systemDefault())
                        .toLocalDateTime();
            } else {
                this.dateTime = null;
            }
        }

        public String getParamName() {
            return paramName;
        }

        public String getValue() {
            return value;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", LastValue.class.getSimpleName() + "[", "]")
                    .add("paramName='" + paramName + "'")
                    .add("value='" + value + "'")
                    .add("dateTime=" + dateTime)
                    .toString();
        }
    }

    public static final class RequestedValue implements Comparable<RequestedValue> {

        private final String paramName;
        private final LocalDateTime requestedDateTime;

        private RequestedValue(String paramName, LocalDateTime requestedDateTime) {
            this.paramName = paramName;
            if (requestedDateTime != null) {
                this.requestedDateTime = requestedDateTime.atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneOffset.systemDefault())
                        .toLocalDateTime();
            } else {
                this.requestedDateTime = null;
            }
        }

        public String getParamName() {
            return paramName;
        }

        public LocalDateTime getRequestedDateTime() {
            return requestedDateTime;
        }

        @Override
        public int compareTo(@NotNull StatData.RequestedValue o) {
            if (o.requestedDateTime == null) {
                return 1;
            }
            if (requestedDateTime == null) {
                return -1;
            }
            return requestedDateTime.compareTo(o.requestedDateTime);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", RequestedValue.class.getSimpleName() + "[", "]")
                    .add("paramName='" + paramName + "'")
                    .add("requestedDateTime=" + requestedDateTime)
                    .toString();
        }
    }
}
