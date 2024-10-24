package ru.tecon.queryBasedDAS.cdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.enterprise.inject.Produces;

/**
 * @author Maksim Shchelkonogov
 * 11.01.2024
 */
public class JsonProducer {

    @Produces
    public Gson getGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    @Produces
    public ObjectMapper getJson() {
        return new ObjectMapper();
    }
}
