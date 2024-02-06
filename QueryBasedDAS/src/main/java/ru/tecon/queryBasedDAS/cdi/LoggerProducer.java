package ru.tecon.queryBasedDAS.cdi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
public class LoggerProducer {

    @Produces
    public Logger getLogger(InjectionPoint injectionPoint) {
        return LoggerFactory.getLogger(injectionPoint.getMember().getDeclaringClass());
    }
}
