package ru.tecon.queryBasedDAS.counter.mfk;

import fish.payara.security.openid.api.OpenIdContext;
import org.jetbrains.annotations.NotNull;
import org.primefaces.PrimeFaces;
import org.primefaces.event.CellEditEvent;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.AlphaNumComparator;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.counter.mfk.ejb.MfkBean;
import ru.tecon.queryBasedDAS.counter.report.ExcelReport;
import ru.tecon.queryBasedDAS.counter.report.PdfReport;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASStatelessBean;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
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
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 03.10.2024
 */
@ViewScoped
@Named("mfkController")
public class MfkConsoleController implements Serializable {

    private final static Comparator<String> COMPARATOR = new AlphaNumComparator();

    @Inject
    private SecurityContext securityContext;

    @Inject
    private OpenIdContext openIdContext;

    @Inject
    private Logger logger;

    @EJB
    private MfkBean mfkBean;

    @EJB
    private QueryBasedDASStatelessBean bean;

    @EJB
    private QueryBasedDASSingletonBean singletonBean;

    private String remoteSelected;
    private StatData selectedStat;
    private List<Config> config = new ArrayList<>();

    private final List<AsyncModel> asyncData = new ArrayList<>();
    private final List<DataModel> requestedDataModel = new ArrayList<>();
    private final List<StatData.LastValue> lastControllerData = new ArrayList<>();
    private final List<ObjectInfoModel> sysParamData = new ArrayList<>();

    private final Map<String, String> groupData = new HashMap<>();
    private LocalDate selectedDate = LocalDate.now();

    private final MfkCounter counter = new MfkCounter();
    private final MfkInfo info = MfkInfo.getInstance();

    private boolean onlyBlock;

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
                .filter(entry -> {
                    if (onlyBlock) {
                        for (String lockName: info.getLocked()) {
                            if (entry.getValue().getCounterName().startsWith(lockName)) {
                                return true;
                            }
                        }
                        return false;
                    } else {
                        return true;
                    }
                })
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(statData -> statData.getObjectName() == null ? "" : statData.getObjectName(), COMPARATOR))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public String checkLocked(String name) {
        for (String lockName: info.getLocked()) {
            if (name.startsWith(lockName)) {
                return "color: salmon;";
            }
        }
        return "";
    }

    public void resetTraffic() throws DasException {
        mfkBean.resetTraffic(selectedStat.getCounterName());
        info.getLocked().removeIf(s -> selectedStat.getCounterName().startsWith(s));
        PrimeFaces.current().ajax().update("statTable");
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

    /**
     * Принудительный запрос данных
     */
    public void requestData() {
        bean.tryLoadHistoricalData(selectedStat.getCounterName(), info.getCounterName(), remoteSelected);
    }

    public void clearConfig() {
        config.clear();
    }

    public void requestSysParam() {
        sysParamData.addAll(mfkBean.getSysInfo(selectedStat.getCounterName()));

        sysParamData.sort((o1, o2) -> COMPARATOR.compare(o1.name, o2.name));

        PrimeFaces.current().executeScript("PF('sysParamWidget').show();");
        PrimeFaces.current().ajax().update("sysParamForm", "sysParamHeader");
    }

    public void clearSysParamData() {
        sysParamData.clear();
    }

    /**
     * Обработик изменения ячейки таблицы
     * @param event событие изменения
     */
    public void onCellEdit(CellEditEvent<?> event) {
        String clientID = event.getColumn().getChildren().get(0).getClientId().replaceAll(":", "\\:");
        PrimeFaces.current().executeScript("document.getElementById('" + clientID + "').parentNode.style.backgroundColor = 'lightgrey'");
    }

    public void writeValues() {
        mfkBean.writeSysInfo(selectedStat.getCounterName(), sysParamData);
    }

    public void synchronizeDate() {
        mfkBean.synchronizeDate(selectedStat.getCounterName());
    }

    public void requestAsync() {
        for (Config configItem: counter.getConfig(selectedStat.getCounterName())) {
            if (configItem.getName().contains(":Текущие данные")) {
                requestedDataModel.add(DataModel.builder(configItem, 0, 0, 0).build());
            }
        }

        List<DataModel> linkedDataModels = bean.tryGetAsyncModelByCounterName(
                info.getCounterName(), selectedStat.getCounterName(), remoteSelected
        );

        for (DataModel dataModel: requestedDataModel) {
            asyncData.add(new AsyncModel(
                    dataModel.getParamName(),
                    linkedDataModels.stream()
                            .map(DataModel::getParamName)
                            .anyMatch(name -> name.equals(dataModel.getParamName()))
            ));
        }

        asyncData.sort(
                Comparator.comparing(AsyncModel::isSelect)
                        .thenComparing(AsyncModel::getParam, COMPARATOR)
        );
        Collections.sort(asyncData);

        PrimeFaces.current().executeScript("PF('asyncDataWidget').show();");
        PrimeFaces.current().ajax().update("asyncDataForm", "asyncDialogHeader");
    }

    public void loadAsyncData() {
        asyncData.forEach(model -> model.setValue(""));

        List<DataModel> dataModels = new ArrayList<>();
        for (DataModel dataModel: requestedDataModel) {
            boolean match = asyncData.stream()
                    .filter(AsyncModel::isSelect)
                    .map(AsyncModel::getParam)
                    .anyMatch(name -> name.equals(dataModel.getParamName()));
            if (match) {
                dataModels.add(dataModel);
            }
        }

        if (!dataModels.isEmpty()) {
            try {
                counter.loadInstantData(dataModels, selectedStat.getCounterName());

                for (DataModel dataModel: dataModels) {
                    for (DataModel.ValueModel valueModel: dataModel.getData()) {
                        asyncData.stream()
                                .filter(model -> model.getParam().equals(dataModel.getParamName()))
                                .forEach(model -> model.setValue(valueModel.getValue()));
                    }
                }
            } catch (DasException e) {
                FacesContext.getCurrentInstance()
                        .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Ошибка", e.getMessage()));
                PrimeFaces.current().ajax().update("growl");
            }
        }
    }

    public String traffic() {
        if (selectedStat != null) {
            return mfkBean.getTraffic(selectedStat.getCounterName());
        }
        return "Неопределенно";
    }

    public void clearAsyncData() {
        asyncData.clear();
        requestedDataModel.clear();
    }

    public void loadLastControllerData() {
        lastControllerData.addAll(mfkBean.getLastValues(selectedStat.getCounterName()));
        lastControllerData.sort((o1, o2) -> COMPARATOR.compare(o1.getParamName(), o2.getParamName()));
    }

    public void clearLastControllerData() {
        lastControllerData.clear();
    }

    /**
     * Загрузка месячной статистики по переданным группам
     */
    public void loadMonthGroupData() {
        groupData.clear();
        groupData.putAll(mfkBean.getGroupData(selectedStat.getCounterName(), selectedDate));
    }

    /**
     * Очистка месячной статистики по переданным группам
     */
    public void clearMonthGroupData() {
        selectedDate = LocalDate.now();
        groupData.clear();
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
                URLEncoder.encode("MFK-1500.xlsx", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            ExcelReport.generateReport(outputStream, "MFK1500", getStatistic());
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
                URLEncoder.encode("MFK1500.pdf", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            PdfReport.generateReport(outputStream, "MFK1500", getStatistic());
            outputStream.flush();
        } catch (IOException e) {
            logger.warn("error send report", e);
        }

        fc.responseComplete();
    }

    /**
     * Создание xlsx отчета по трафику
     */
    public void createTrafficExcelReport() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        ec.responseReset();
        ec.setResponseContentType("application/vnd.ms-excel; charset=UTF-8");
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" +
                URLEncoder.encode("Трафик", StandardCharsets.UTF_8) + " " +
                URLEncoder.encode("MFK-1500.xlsx", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            List<StatData> statistic = getStatistic();
            Map<String, List<String>> servers = statistic.stream()
                    .map(StatData::getCounterName)
                    .collect(Collectors.groupingBy(s -> s.split("_")[0]));

            List<TrafficReportStatistic> traffic = new ArrayList<>();
            mfkBean.getTraffic(servers)
                    .forEach((k, v) -> statistic.stream()
                            .filter(value -> value.getCounterName().startsWith(k))
                            .findFirst()
                            .ifPresent(value -> traffic.add(
                                    new TrafficReportStatistic(value.getObjectName(), value.getCounterName(), v))
                            ));

            traffic.sort(Comparator.comparing(statData -> statData.getObjectName() == null ? "" : statData.getObjectName(), COMPARATOR));

            ExcelTrafficReport.generateReport(outputStream, "MFK1500", traffic);
            outputStream.flush();
        } catch (IOException e) {
            logger.warn("error send report", e);
        }

        fc.responseComplete();
    }

    /**
     * Создание pdf отчета по трафику
     */
    public void createTrafficPdfReport() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        ec.responseReset();
        ec.setResponseContentType("application/vnd.ms-excel; charset=UTF-8");
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" +
                URLEncoder.encode("Трафик", StandardCharsets.UTF_8) + " " +
                URLEncoder.encode("MFK1500.pdf", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            List<StatData> statistic = getStatistic();
            Map<String, List<String>> servers = statistic.stream()
                    .map(StatData::getCounterName)
                    .collect(Collectors.groupingBy(s -> s.split("_")[0]));

            List<TrafficReportStatistic> traffic = new ArrayList<>();
            mfkBean.getTraffic(servers)
                    .forEach((k, v) -> statistic.stream()
                        .filter(value -> value.getCounterName().startsWith(k))
                        .findFirst()
                        .ifPresent(value -> traffic.add(
                                new TrafficReportStatistic(value.getObjectName(), value.getCounterName(), v))
                    ));

            traffic.sort(Comparator.comparing(statData -> statData.getObjectName() == null ? "" : statData.getObjectName(), COMPARATOR));

            PdfTrafficReport.generateReport(outputStream, "MFK1500", traffic);
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

    public Set<Map.Entry<String, String>> getGroupData() {
        return groupData.entrySet();
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
    }

    public LocalDate getMaxDate() {
        return LocalDate.now();
    }

    public LocalDate getMinDate() {
        return LocalDate.now().minusDays(30);
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

    public List<AsyncModel> getAsync() {
        return asyncData;
    }

    public List<StatData.LastValue> getLastControllerData() {
        return lastControllerData;
    }

    public List<ObjectInfoModel> getSysParamData() {
        return sysParamData;
    }

    public boolean isOnlyBlock() {
        return onlyBlock;
    }

    public void setOnlyBlock(boolean onlyBlock) {
        this.onlyBlock = onlyBlock;
    }

    public static class AsyncModel implements Comparable<AsyncModel> {

        private final String param;
        private boolean select;
        private String value = "";

        public AsyncModel(String param, boolean select) {
            this.param = param;
            this.select = select;
        }

        public boolean isSelect() {
            return select;
        }

        public void setSelect(boolean select) {
            this.select = select;
        }

        public String getParam() {
            return param;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int compareTo(@NotNull AsyncModel o) {
            return Boolean.compare(o.select, select);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", AsyncModel.class.getSimpleName() + "[", "]")
                    .add("param='" + param + "'")
                    .add("select=" + select)
                    .add("value='" + value + "'")
                    .toString();
        }
    }

    public static class ObjectInfoModel {

        private final String name;
        private String value;
        private final boolean write;
        private boolean change;

        public ObjectInfoModel(String name, String value, boolean write) {
            this.name = name;
            this.value = value;
            this.write = write;
        }

        public void setValue(String value) {
            if (write) {
                this.value = value;
                change = true;
            }
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public boolean isWrite() {
            return write;
        }

        public boolean isChange() {
            return change;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ObjectInfoModel.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .add("value='" + value + "'")
                    .add("write=" + write)
                    .toString();
        }
    }

    public static class TrafficReportStatistic {

        private final String objectName;
        private final String counterName;
        private final String traffic;

        public TrafficReportStatistic(String objectName, String counterName, String traffic) {
            this.objectName = objectName;
            this.counterName = counterName;
            this.traffic = traffic;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getCounterName() {
            return counterName;
        }

        public String getTraffic() {
            return traffic;
        }
    }
}
