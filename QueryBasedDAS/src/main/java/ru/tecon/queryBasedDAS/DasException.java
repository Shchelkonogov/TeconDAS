package ru.tecon.queryBasedDAS;

/**
 * @author Maksim Shchelkonogov
 * 18.01.2024
 */
public class DasException extends Exception {

    public DasException(String message) {
        super(message);
    }

    public DasException(String message, Throwable cause) {
        super(message, cause);
    }
}
