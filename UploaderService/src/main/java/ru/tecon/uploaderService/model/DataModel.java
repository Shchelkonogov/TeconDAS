package ru.tecon.uploaderService.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Информация о собранных данных по объектам, для загрузки в базу данных
 *
 * @author Maksim Shchelkonogov
 * 26.01.2024
 */
public final class DataModel implements Comparable<DataModel>, Serializable {

    private final String paramName;
    private final int objectId;
    private final int paramId;
    private final int aggregateId;

    private String incrementValue;
    private LocalDateTime startDateTime;

    private final List<ValueModel> data = new ArrayList<>();

    public DataModel(String paramName, int objectId, int paramId, int aggregateId) {
        this.paramName = paramName;
        this.objectId = objectId;
        this.paramId = paramId;
        this.aggregateId = aggregateId;
    }

    public DataModel(String paramName, int objectId, int paramId, int aggregateId, String incrementValue) {
        this(paramName, objectId, paramId, aggregateId);
        this.incrementValue = incrementValue;
    }

    public DataModel(String paramName, int objectId, int paramId, int aggregateId, String incrementValue, LocalDateTime startDateTime) {
        this(paramName, objectId, paramId, aggregateId, incrementValue);
        this.startDateTime = startDateTime;
    }

    public void addData(String value, LocalDateTime time) {
        if ((incrementValue != null) && !incrementValue.equals("")) {
            value = new BigDecimal(value).multiply(new BigDecimal(incrementValue)).toString();
        }
        data.add(new ValueModel(value, time));
    }

    public void addData(String value, LocalDateTime time, int quality) {
        if ((incrementValue != null) && !incrementValue.equals("")) {
            value = new BigDecimal(value).multiply(new BigDecimal(incrementValue)).toString();
        }
        data.add(new ValueModel(value, time, quality));
    }

    public String getParamName() {
        return paramName;
    }

    public int getObjectId() {
        return objectId;
    }

    public int getParamId() {
        return paramId;
    }

    public int getAggregateId() {
        return aggregateId;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public List<ValueModel> getData() {
        return data;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DataModel.class.getSimpleName() + "[", "]")
                .add("paramName='" + paramName + "'")
                .add("objectId=" + objectId)
                .add("paramId=" + paramId)
                .add("aggregateId=" + aggregateId)
                .add("incrementValue='" + incrementValue + "'")
                .add("startDateTime=" + startDateTime)
                .add("data=" + data)
                .toString();
    }

    @Override
    public int compareTo(DataModel o) {
        if (o.startDateTime == null) {
            return 1;
        }
        if (startDateTime == null) {
            return -1;
        }
        return startDateTime.compareTo(o.startDateTime);
    }

    public final static class ValueModel implements Serializable {

        private final String value;
        private final LocalDateTime dateTime;
        private int quality = 192;

        private ValueModel(String value, LocalDateTime dateTime) {
            this.value = value;
            this.dateTime = dateTime;
        }

        private ValueModel(String value, LocalDateTime dateTime, int quality) {
            this.value = value;
            this.dateTime = dateTime;
            this.quality = quality;
        }

        public String getValue() {
            return value;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public int getQuality() {
            return quality;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ValueModel.class.getSimpleName() + "[", "]")
                    .add("value='" + value + "'")
                    .add("time=" + dateTime)
                    .add("quality=" + quality)
                    .toString();
        }
    }
}
