package ru.tecon.queryBasedDAS.counter.asDTS;

import fish.payara.security.openid.api.OpenIdContext;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.AlphaNumComparator;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.counter.report.ExcelReport;
import ru.tecon.queryBasedDAS.counter.report.PdfReport;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASStatelessBean;
import ru.tecon.uploaderService.model.Config;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.security.enterprise.SecurityContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 03.05.2024
 */
@ViewScoped
@Named("asdtsController")
public class ASDTSConsoleController implements Serializable {

    private final static Comparator<String> COMPARATOR = new AlphaNumComparator();

    @Inject
    private SecurityContext securityContext;

    @Inject
    private OpenIdContext openIdContext;

    @Inject
    private Logger logger;

    @EJB
    private QueryBasedDASStatelessBean bean;

    @EJB
    private QueryBasedDASSingletonBean singletonBean;

    private String remoteSelected;
    private StatData selectedStat;
    private List<Config> config = new ArrayList<>();

    private final ASDTSCounter counter = new ASDTSCounter();
    private final ASDTSInfo info = ASDTSInfo.getInstance();

    @PostConstruct
    private void init() {
        remoteSelected = getRemotes().get(0);
    }

    /**
     * Получения статистики
     *
     * @return статистика
     */
    public List<StatData> getStatistic() {
        return info.getStatistic().entrySet().stream()
                .filter(entry -> entry.getKey().getServer().equals(remoteSelected))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(statData -> statData.getObjectName() == null ? "" : statData.getObjectName(), COMPARATOR))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Запрос на конфигурацию счетчика
     */
    public void requestConfig() {
        Set<Config> config_ = counter.getConfig(selectedStat.getCounterName());
        config = new ArrayList<>(config_);
        config.sort((o1, o2) -> COMPARATOR.compare(o1.getName(), o2.getName()));
        bean.tryUploadConfigByCounterName(info.getCounterName(), selectedStat.getCounterName(), remoteSelected, config_);
    }

    public void clearConfig() {
        config.clear();
    }

    public void clearStatistic() {
        info.clearStatistic(statKey -> statKey.getServer().equals(remoteSelected));
    }

    /**
     * Загрузка всех известных объектов счетчика
     */
    public void loadObjects() {
        Map<String, List<String>> counterObjects = bean.getCounterObjects(info.getCounterName());
        bean.uploadCounterObjects(remoteSelected, counterObjects);
    }

    /**
     * Изменение частоты опроса
     *
     * @param period частота опроса
     */
    public void changePeriodicity(String period) {
        singletonBean.getCounterProp(remoteSelected, info.getCounterName()).setPeriodicity(Periodicity.valueOf(period));
    }

    /**
     * Получения списка возможных частот опроса
     *
     * @return список частот опроса
     */
    public Periodicity[] getPeriodicityMenu() {
        return Periodicity.values();
    }

    /**
     * Получение названия иконок, в зависимости от того какая установлена.
     * Если проверяется выбранная частота опроса, то иконка будет закрашенная
     *
     * @param period частота опроса для проверки
     * @return название иконки
     */
    public String getPeriodicityMenuIcon(String period) {
        if (singletonBean.getCounterProp(remoteSelected, info.getCounterName()).getPeriodicity() == Periodicity.valueOf(period)) {
            return "pi pi-fw pi-circle-fill";
        } else {
            return "pi pi-fw pi-circle";
        }
    }

    /**
     * Создание xlsx отчета
     */
    public void createExcelReport() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        ec.responseReset();
        ec.setResponseContentType("application/vnd.ms-excel; charset=UTF-8");
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" +
                URLEncoder.encode("Статистика", StandardCharsets.UTF_8) + " " +
                URLEncoder.encode("АС", StandardCharsets.UTF_8) + " " +
                URLEncoder.encode("ДТС.xlsx", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            ExcelReport.generateReport(outputStream, "АС 'ДТС'", getStatistic());
            outputStream.flush();
        } catch (IOException e) {
            logger.warn("error send report", e);
        }

        fc.responseComplete();
    }

    /**
     * Создание pdf отчета
     */
    public void createPdfReport() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        ec.responseReset();
        ec.setResponseContentType("application/vnd.ms-excel; charset=UTF-8");
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" +
                URLEncoder.encode("Статистика", StandardCharsets.UTF_8) + " " +
                URLEncoder.encode("АС", StandardCharsets.UTF_8) + " " +
                URLEncoder.encode("ДТС.pdf", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            PdfReport.generateReport(outputStream, "АС 'ДТС'", getStatistic());
            outputStream.flush();
        } catch (IOException e) {
            logger.warn("error send report", e);
        }

        fc.responseComplete();
    }

    /**
     * Получение количества используемых потоков для опроса
     *
     * @return количество потоков
     */
    public int getThreadCount() {
        return singletonBean.getCounterProp(remoteSelected, info.getCounterName()).getConcurrencyDepth();
    }

    /**
     * Установка количества используемых потоков для опроса
     *
     * @param threadCount количество потоков
     */
    public void setThreadCount(int threadCount) {
        singletonBean.getCounterProp(remoteSelected, info.getCounterName()).setConcurrencyDepth(threadCount);
    }

    public String getPrincipal() {
        return openIdContext.getClaimsJson().getString("name");
    }

    public boolean isAdmin() {
        return securityContext.isCallerInRole("admin");
    }

    public List<String> getRemotes() {
        List<String> result = new ArrayList<>(singletonBean.getRemotes().keySet());
        result.removeIf(remote -> !singletonBean.counterNameSet(remote).contains(info.getCounterName()));
        result.sort(Comparator.comparing(remote -> singletonBean.getRemote(remote).getPriority()));
        return result;
    }

    public String isRemoteEnable(String remote) {
        return singletonBean.getRemoteProp(remote).isEnable() ? "(вкл)" : "(выкл)";
    }

    public String getRemoteSelected() {
        return remoteSelected;
    }

    public void setRemoteSelected(String remoteSelected) {
        this.remoteSelected = remoteSelected;
    }

    public StatData getSelectedStat() {
        return selectedStat;
    }

    public void setSelectedStat(StatData selectedStat) {
        this.selectedStat = selectedStat;
    }

    public List<Config> getConfig() {
        return config;
    }
}
