package ru.tecon.uploaderService.cdi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.enterprise.inject.Produces;

/**
 * @author Maksim Shchelkonogov
 * 21.05.2024
 */
public class JsonProducer {

    @Produces
    public Gson getGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
