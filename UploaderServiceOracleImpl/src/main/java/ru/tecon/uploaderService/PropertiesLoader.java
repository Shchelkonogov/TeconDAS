package ru.tecon.uploaderService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Maksim Shchelkonogov
 * 10.01.2024
 */
public class PropertiesLoader {

    public static Properties loadProperties(String resourceFileName) throws IOException {
        Properties configuration = new Properties();
        try (InputStream inputStream = PropertiesLoader.class.getClassLoader().getResourceAsStream(resourceFileName)) {
            configuration.load(inputStream);
        }
        return configuration;
    }
}
