package ru.tecon.queryBasedDAS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * @author Maksim Shchelkonogov
 * 10.01.2024
 */
public class PropertiesLoader {

    private PropertiesLoader() {
    }

    public static Properties loadProperties(String resourceFileName) throws IOException {
        Properties configuration = new Properties();
        try (InputStream inputStream = PropertiesLoader.class.getClassLoader().getResourceAsStream(resourceFileName)) {
            if (inputStream != null) {
                configuration.load(inputStream);
            }
        }
        return configuration;
    }

    public static void storeProperties(String resourceFileName, Properties prop) throws IOException {
        Path path = Paths.get(Paths.get("").toAbsolutePath() + "/" + resourceFileName);
        Files.createDirectories(path.getParent());
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            prop.store(outputStream, null);
        }
    }
}
