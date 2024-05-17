package ru.tecon.queryBasedDAS.cdi.view;

import fish.payara.security.openid.api.OpenIdContext;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.security.enterprise.SecurityContext;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 29.03.2024
 */
@ViewScoped
@Named("console")
public class ConsoleController implements Serializable {

    @Inject
    private SecurityContext securityContext;

    @Inject
    private OpenIdContext openIdContext;

    @EJB
    private QueryBasedDASSingletonBean bean;

    private String remoteSelected;
    private String counterForUpdate;

    @PostConstruct
    private void init() {
        remoteSelected = getRemotes()[0];
    }

    public Map<String, String> getAllConsoleMap() {
        return bean.getAllConsole()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (s, s2) -> s + "\n" + s2));
    }

    @PreDestroy
    private void destroy() {
    }

    public Set<String> getCounters() {
        return bean.getAllConsole().keySet();
    }

    public Periodicity[] getPeriodicityMenu() {
        return Periodicity.values();
    }

    public String getPeriodicityMenuIcon(String counter, String period) {
        if (bean.getCounterWebConsole(counter) != null) {
            String periodicity = bean.getCounterProperty(counter, "periodicity");
            if (periodicity == null) {
                periodicity = bean.getProperty("periodicity");
            }
            return periodicity.equals(period) ? "pi pi-fw pi-circle-fill" : "pi pi-fw pi-circle";
        }

        return "pi pi-fw pi-circle";
    }

    public void changePeriodicity(String counter, String period) {
        bean.setCounterProperty(counter, "periodicity", period);
    }

    public int initCounterForUpdate(String counter) {
        counterForUpdate = counter;
        return getThreadCount();
    }

    public int getThreadCount() {
        if (counterForUpdate != null) {
            String depth = bean.getCounterProperty(counterForUpdate, "concurrencyDepth");
            if (depth == null) {
                depth = bean.getProperty("concurrencyDepth");
            }
            return Integer.parseInt(depth);
        } else {
            return 0;
        }
    }

    public void setThreadCount(int threadCount) {
        if (counterForUpdate != null) {
            bean.setCounterProperty(counterForUpdate, "concurrencyDepth", String.valueOf(threadCount));
        }
    }

    public String getCounterForUpdate() {
        return counterForUpdate;
    }

    public String[] getRemotes() {
        return bean.getProperty("uploadServerNames").split(" ");
    }

    public boolean isAdmin() {
        return securityContext.isCallerInRole("admin");
    }

    public String getPrincipal() {
        return openIdContext.getClaimsJson().getString("name");
    }

    public String getRemoteSelected() {
        return remoteSelected;
    }

    public void setRemoteSelected(String remoteSelected) {
        this.remoteSelected = remoteSelected;
    }
}
