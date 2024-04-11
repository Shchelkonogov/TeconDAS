package ru.tecon.queryBasedDAS.cdi.view;

import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import java.util.Map;

/**
 * @author Maksim Shchelkonogov
 * 29.03.2024
 */
@Named("console")
@RequestScoped
public class ConsoleController {

    @EJB
    private QueryBasedDASSingletonBean bean;

    @PostConstruct
    private void init() {
        System.out.println("init");
    }

    public Map<String, String> getAllConsoleMap() {
        return bean.getAllConsole();
    }

    @PreDestroy
    private void destroy() {
        System.out.println("destroy");
    }
}
