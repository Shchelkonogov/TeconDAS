package ru.tecon.queryBasedDAS.ejb.observers;

import java.time.LocalDateTime;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
public final class TimerEvent {

    private final String eventInfo;
    private final LocalDateTime time = LocalDateTime.now();

    public TimerEvent(String s) {
        this.eventInfo = s;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public String getEventInfo() {
        return eventInfo;
    }

    @Override
    public String toString() {
        return "TimerEvent {" +
                "eventInfo='" + eventInfo + '\'' +
                ", time=" + time +
                '}';
    }
}
