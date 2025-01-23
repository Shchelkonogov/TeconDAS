package ru.tecon.queryBasedDAS.counter.assd.prop;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Maksim Shchelkonogov
 * 26.12.2024
 */
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, TYPE, METHOD})
public @interface Json {

    JsonType type();
}
