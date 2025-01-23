package ru.tecon.queryBasedDAS;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Maksim Shchelkonogov
 * 22.01.2025
 */
public class SerializeUtil {

    public static void serializeSub(ConcurrentHashMap<String, Set<String>> sub, Path path) throws DasException {
        try (OutputStream os = Files.newOutputStream(path);
             ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(new Sub(sub));
        } catch (IOException e) {
            throw new DasException("serialize exception", e);
        }
    }

    public static ConcurrentHashMap<String, Set<String>> deserializeSub(Path path) throws DasException {
        try (InputStream is = Files.newInputStream(path);
             ObjectInputStream ois = new ObjectInputStream(is)) {
            return ((Sub) ois.readObject()).sub;
        } catch (IOException | ClassNotFoundException e) {
            throw new DasException("deserialize exception", e);
        }
    }

    private static class Sub implements Serializable {

        private final ConcurrentHashMap<String, Set<String>> sub;

        public Sub(ConcurrentHashMap<String, Set<String>> sub) {
            this.sub = sub;
        }
    }
}
