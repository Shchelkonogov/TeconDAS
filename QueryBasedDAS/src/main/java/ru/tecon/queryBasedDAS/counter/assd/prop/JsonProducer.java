package ru.tecon.queryBasedDAS.counter.assd.prop;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import javax.enterprise.inject.Produces;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Maksim Shchelkonogov
 * 11.01.2024
 */
public class JsonProducer {

    private static final String DATE_TIME_FORMAT_READ = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String DATE_TIME_FORMAT_WRITE = "yyyy-MM-dd HH:mm:ss";

    @Produces
    @Json(type = JsonType.RESPONSE)
    public ObjectMapper getJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JavaTimeModule timeModule = new JavaTimeModule();
        timeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_READ)));

        objectMapper.registerModules(timeModule);
        return objectMapper;
    }

    @Produces
    @Json(type = JsonType.REQUEST)
    public ObjectMapper getJsonSnake() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JavaTimeModule timeModule = new JavaTimeModule();
        timeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_WRITE)));

        objectMapper.registerModules(timeModule);
        return objectMapper;
    }
}
