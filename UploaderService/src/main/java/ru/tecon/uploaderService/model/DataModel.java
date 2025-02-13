package ru.tecon.uploaderService.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Информация о собранных данных по объектам, для загрузки в базу данных
 *
 * @author Maksim Shchelkonogov
 * 26.01.2024
 */
public final class DataModel implements Comparable<DataModel>, Serializable {

    private final Config param;
    private final int objectId;
    private final int paramId;
    private final int aggregateId;

    private String objectName;
    private String incrementValue;
    private LocalDateTime startDateTime;
    private String lastValue;

    private final Set<ValueModel> data = new TreeSet<>();

    private DataModel(Config param, int objectId, int paramId, int aggregateId) {
        this.param = param;
        this.objectId = objectId;
        this.paramId = paramId;
        this.aggregateId = aggregateId;
    }

    public void addData(String value, LocalDateTime time) {
        if ((value != null) && !value.isEmpty()) {
            if ((incrementValue != null) && !incrementValue.isEmpty()) {
                value = new BigDecimal(value).multiply(new BigDecimal(incrementValue)).toString();
            }
            data.add(new ValueModel(value, time));
        }
    }

    public void addData(String value, LocalDateTime time, int quality) {
        if ((value != null) && !value.isEmpty()) {
            if ((incrementValue != null) && !incrementValue.isEmpty()) {
                value = new BigDecimal(value).multiply(new BigDecimal(incrementValue)).toString();
            }
            data.add(new ValueModel(value, time, quality));
        }
    }

    public static Builder builder(Config param, int objectId, int paramId, int aggregateId) {
        return new Builder(param, objectId, paramId, aggregateId);
    }

    public String getParamName() {
        return param.getName();
    }

    public String getParamSysInfo() {
        return param.getSysInfo();
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

    public String getObjectName() {
        return objectName;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public String getLastValue() {
        return lastValue;
    }

    public Set<ValueModel> getData() {
        return data;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DataModel.class.getSimpleName() + "[", "]")
                .add("param=" + param)
                .add("objectId=" + objectId)
                .add("paramId=" + paramId)
                .add("aggregateId=" + aggregateId)
                .add("objectName='" + objectName + "'")
                .add("incrementValue='" + incrementValue + "'")
                .add("startDateTime=" + startDateTime)
                .add("lastValue='" + lastValue + "'")
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

    public final static class ValueModel implements Comparable<ValueModel>, Serializable {

        private final String value;
        private String modifyValue;
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

        public boolean isModified() {
            return (modifyValue != null) && !modifyValue.isEmpty();
        }

        public String getModifyValue() {
            return modifyValue;
        }

        public void setModifyValue(String modifyValue) {
            this.modifyValue = modifyValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValueModel that = (ValueModel) o;
            return dateTime.equals(that.dateTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dateTime);
        }


        @Override
        public int compareTo(@NotNull ValueModel o) {
            return this.dateTime.compareTo(o.getDateTime());
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

    public static final class Builder {

        private final Config param;
        private final int objectId;
        private final int paramId;
        private final int aggregateId;

        private String objectName;
        private String incrementValue;
        private LocalDateTime startDateTime;
        private String lastValue;

        private Builder(Config param, int objectId, int paramId, int aggregateId) {
            this.param = param;
            this.objectId = objectId;
            this.paramId = paramId;
            this.aggregateId = aggregateId;
        }

        public Builder incrementValue(String incrementValue) {
            this.incrementValue = incrementValue;
            return this;
        }

        public Builder startDateTime(LocalDateTime startDateTime) {
            this.startDateTime = startDateTime;
            return this;
        }

        public Builder lastValue(String lastValue) {
            this.lastValue = lastValue;
            return this;
        }

        public Builder objectName(String objectName) {
            this.objectName = objectName;
            return this;
        }

        public DataModel build() {
            DataModel dataModel = new DataModel(param, objectId, paramId, aggregateId);
            dataModel.incrementValue = incrementValue;
            dataModel.startDateTime = startDateTime;
            dataModel.objectName = objectName;
            dataModel.lastValue = lastValue;
            return dataModel;
        }
    }
}
