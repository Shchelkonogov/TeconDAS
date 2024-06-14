package ru.tecon.queryBasedDAS.cdi.view;

import fish.payara.security.openid.api.OpenIdContext;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.ejb.ListenerServicesStatelessBean;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.security.enterprise.SecurityContext;
import java.io.Serializable;
import java.util.*;
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

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASSingletonBean bean;

    @EJB
    private ListenerServicesStatelessBean listenerBean;

    private String remoteSelected;
    private String counterForUpdate;

    @PostConstruct
    private void init() {
        remoteSelected = getRemotes().get(0);
    }

    public Map<String, String> getAllConsoleMap() {
        Set<String> counters = bean.counterNameSet(remoteSelected);
        return bean.getAllConsole()
                .entrySet()
                .stream()
                .filter(entry -> counters.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (s, s2) -> s + "\n" + s2));
    }

    @PreDestroy
    private void destroy() {
    }

    public Set<String> getCounters() {
        Set<String> counters = bean.counterNameSet(remoteSelected);
        Set<String> consoleCounters = bean.getAllConsole().keySet();
        consoleCounters.removeIf(counter -> !counters.contains(counter));
        return consoleCounters;
    }

    public Periodicity[] getPeriodicityMenu() {
        return Periodicity.values();
    }

    public String getPeriodicityMenuIcon(String counter, String period) {
        if (bean.getCounterWebConsole(counter) != null) {
            if (bean.getCounterProp(remoteSelected, counter).getPeriodicity() == Periodicity.valueOf(period)) {
                return "pi pi-fw pi-circle-fill";
            } else {
                return "pi pi-fw pi-circle";
            }
        }

        return "pi pi-fw pi-circle";
    }

    public void changePeriodicity(String counter, String period) {
        bean.getCounterProp(remoteSelected, counter).setPeriodicity(Periodicity.valueOf(period));
    }

    public int initCounterForUpdate(String counter) {
        counterForUpdate = counter;
        return getThreadCount();
    }

    public int getThreadCount() {
        if (counterForUpdate != null) {
            return bean.getCounterProp(remoteSelected, counterForUpdate).getConcurrencyDepth();
        } else {
            return 0;
        }
    }

    public void setThreadCount(int threadCount) {
        if (counterForUpdate != null) {
            bean.getCounterProp(remoteSelected, counterForUpdate).setConcurrencyDepth(threadCount);
        }
    }

    public String getRemoteEnableIcon() {
        return bean.getRemoteProp(remoteSelected).isEnable() ? "pi pi-fw pi-circle-fill" : "pi pi-fw pi-circle";
    }

    public void changeRemoteEnable() {
        boolean newValue = !bean.getRemoteProp(remoteSelected).isEnable();

        if (!newValue) {
            try {
                listenerBean.unregisterConfigRequestListener(remoteSelected);
                listenerBean.unregisterAsyncRequestListener(remoteSelected);
            } catch (DasException e) {
                logger.warn("error register listeners", e);
            }
        }

        bean.getRemoteProp(remoteSelected).setEnable(!bean.getRemoteProp(remoteSelected).isEnable());

        if (newValue) {
            try {
                listenerBean.registerConfigRequestListener(remoteSelected);
                listenerBean.registerAsyncRequestListener(remoteSelected);
            } catch (DasException e) {
                logger.warn("error register listeners", e);
            }
        }
    }

    public String getRemoteAlarmEnableIcon() {
        return bean.getRemoteProp(remoteSelected).isEnableAlarm() ? "pi pi-fw pi-circle-fill" : "pi pi-fw pi-circle";
    }

    public void changeRemoteAlarmEnable() {
        bean.getRemoteProp(remoteSelected).setEnableAlarm(!bean.getRemoteProp(remoteSelected).isEnableAlarm());
    }

    public String getCounterForUpdate() {
        return counterForUpdate;
    }

    public List<String> getRemotes() {
        List<String> result = new ArrayList<>(bean.getRemotes().keySet());
        result.sort(Comparator.comparing(remote -> bean.getRemote(remote).getPriority()));
        return result;
    }

    public String isRemoteEnable(String remote) {
        return bean.getRemoteProp(remote).isEnable() ? "(вкл)" : "(выкл)";
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
