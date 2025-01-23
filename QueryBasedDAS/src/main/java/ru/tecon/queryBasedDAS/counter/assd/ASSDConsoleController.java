package ru.tecon.queryBasedDAS.counter.assd;

import fish.payara.security.openid.api.OpenIdContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.AlphaNumComparator;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterType;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 20.12.2024
 */
@ViewScoped
@Named("assdController")
public class ASSDConsoleController implements Serializable {

    private final static Comparator<String> COMPARATOR = new AlphaNumComparator();
    private static final Pattern PATTERN_MUID = Pattern.compile(".* \\((?<muid>\\d+)\\)$");

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
    private StatDataWrapper selectedStat;
    private List<Config> config = new ArrayList<>();

    private final ASSDCounter counter = new ASSDCounter();
    private final ASSDCounterInfo info = ASSDCounterInfo.getInstance();

    @PostConstruct
    private void init() {
        remoteSelected = getRemotes().get(0);
    }

    /**
     * Получения статистики
     *
     * @return статистика
     */
    public List<StatDataWrapper> getStatistic() {
        return info.getStatistic().entrySet().stream()
                .filter(entry -> entry.getKey().getServer().equals(remoteSelected))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(statData -> statData.getObjectName() == null ? "" : statData.getObjectName(), COMPARATOR))
                .map(statData -> new StatDataWrapper(statData, isSubscribed(statData.getCounterName())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public String getMuid(String name) {
        Matcher m = PATTERN_MUID.matcher(name);
        if (m.find()) {
            return m.group("muid");
        }

        return UUID.randomUUID().toString();
    }

    /**
     * Запрос на конфигурацию счетчика
     */
    public void requestConfig() {
        Set<Config> config_ = counter.getConfig(selectedStat.statData.getCounterName());
        config = new ArrayList<>(config_);
        config.sort((o1, o2) -> COMPARATOR.compare(o1.getName(), o2.getName()));
        bean.tryUploadConfigByCounterName(info.getCounterName(), selectedStat.statData.getCounterName(), remoteSelected, config_);
    }

    /**
     * Принудительный запрос данных
     */
    public void requestData() {
        bean.tryLoadHistoricalData(selectedStat.statData.getCounterName(), info.getCounterName(), remoteSelected);
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

    public boolean isSubscribed(String objectName) {
        return singletonBean.getObjectRemoteSub(objectName).contains(remoteSelected);
    }

    public void subscribe(String objectName) {
        Set<String> objectRemoteSub = singletonBean.getObjectRemoteSub(objectName);
        if (objectRemoteSub.contains(remoteSelected)) {
            if (objectRemoteSub.size() == 1) {
                try {
                    counter.unsubscribe(objectName);
                } catch (DasException e) {
                    logger.error("Error unsubscribe", e);
                }
            }
            singletonBean.removeSubObject(remoteSelected, objectName);
        } else {
            if (objectRemoteSub.isEmpty()) {
                try {
                    counter.subscribe(objectName);
                } catch (DasException e) {
                    logger.error("Error subscribe", e);
                }
                singletonBean.putSubObject(remoteSelected, objectName);
            }
        }
    }

    /**
     * Определяет тип работы счетчика. Является ли он опросным, если нет, значит подписка
     *
     * @return тип счетчика
     */
    public boolean isQueryType() {
        return info.getCounterType() == CounterType.QUERY;
    }

    public String getColumnName1() {
        switch (info.getCounterType()) {
            case QUERY:
                return "Начало опроса";
            case SUBSCRIPTION:
                return "Начало разбора";
        }
        return "";
    }

    public String getColumnName2() {
        switch (info.getCounterType()) {
            case QUERY:
                return "Время опроса, мс";
            case SUBSCRIPTION:
                return "Время разбора, мс";
        }
        return "";
    }

    public String getColumnName3() {
        switch (info.getCounterType()) {
            case QUERY:
                return "Диапазон запрашиваемых измерений";
            case SUBSCRIPTION:
                return "Диапазон подписанных измерений";
        }
        return "";
    }

    public String getMenuName() {
        switch (info.getCounterType()) {
            case QUERY:
                return "Запрашиваемые данные";
            case SUBSCRIPTION:
                return "Подписанные данные";
        }
        return "";
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
                URLEncoder.encode("АССД.xlsx", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            switch (info.getCounterType()) {
                case QUERY:
                    ExcelReport.generateReport(outputStream, "АССД", getStatistic().stream().map(StatDataWrapper::getStatData).collect(Collectors.toList()));
                    break;
                case SUBSCRIPTION:
                    String[] header = {"№", "Имя объекта", "Имя прибора", "Загрузка последних данных",
                            "Дата последних данных", "Диапазон подписанных измерений"};
                    ExcelReport.generateReport(outputStream, "АССД", getStatistic().stream().map(StatDataWrapper::getStatData).collect(Collectors.toList()), header);
                    break;
            }
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
                URLEncoder.encode("АССД.pdf", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            switch (info.getCounterType()) {
                case QUERY:
                    PdfReport.generateReport(outputStream, "АССД", getStatistic().stream().map(StatDataWrapper::getStatData).collect(Collectors.toList()));
                    break;
                case SUBSCRIPTION:
                    String[] header = {"№", "Имя объекта", "Имя прибора", "Загрузка последних данных",
                            "Дата последних данных", "Диапазон подписанных измерений"};
                    PdfReport.generateReport(outputStream, "АССД", getStatistic().stream().map(StatDataWrapper::getStatData).collect(Collectors.toList()), header);
                    break;
            }
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

    public StatDataWrapper getSelectedStat() {
        return selectedStat;
    }

    public void setSelectedStat(StatDataWrapper selectedStat) {
        this.selectedStat = selectedStat;
    }

    public List<Config> getConfig() {
        return config;
    }

    @Data
    @AllArgsConstructor
    public static class StatDataWrapper {
        public StatData statData;
        public boolean sub;
    }
}
