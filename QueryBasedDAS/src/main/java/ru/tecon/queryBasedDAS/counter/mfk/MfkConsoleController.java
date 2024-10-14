package ru.tecon.queryBasedDAS.counter.mfk;

import fish.payara.security.openid.api.OpenIdContext;
import org.jetbrains.annotations.NotNull;
import org.primefaces.PrimeFaces;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 * 03.10.2024
 */
@ViewScoped
@Named("mfkController")
public class MfkConsoleController implements Serializable {

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
    private Set<Config> config = new HashSet<>();

    private final List<AsyncModel> asyncData = new ArrayList<>();
    private final List<DataModel> requestedDataModel = new ArrayList<>();
    private final List<StatData.LastValue> lastControllerData = new ArrayList<>();

    private final MfkCounter counter = new MfkCounter();
    private final MfkInfo info = MfkInfo.getInstance();

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
                .sorted(Comparator.comparing(statData -> statData.getObjectName() == null ? "" : statData.getObjectName(),
                        new AlphaNumComparator()))
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

    public void resetTraffic() {
        mfkBean.resetTraffic(selectedStat.getCounterName());
    }

    /**
     * Запрос на конфигурацию счетчика
     */
    public void requestConfig() {
        config = counter.getConfig(selectedStat.getCounterName());
        bean.tryUploadConfigByCounterName(info.getCounterName(), selectedStat.getCounterName(), remoteSelected, config);
    }

    public void clearConfig() {
        config.clear();
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

    public void clearAsyncData() {
        asyncData.clear();
        requestedDataModel.clear();
    }

    public void loadLastControllerData() {
        lastControllerData.addAll(mfkBean.getLastValues(selectedStat.getCounterName()));
    }

    public void clearLastControllerData() {
        lastControllerData.clear();
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

    public Set<Config> getConfig() {
        return config;
    }

    public List<AsyncModel> getAsync() {
        return asyncData;
    }

    public List<StatData.LastValue> getLastControllerData() {
        return lastControllerData;
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
}
