package ru.tecon.queryBasedDAS.counter.ftp.mct20;

import fish.payara.security.openid.api.OpenIdContext;
import org.primefaces.PrimeFaces;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.AlphaNumComparator;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.Counter;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterAsyncRequest;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterExtension;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.plain.PlainCounter;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.sa94.SA94Counter;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.slave.SLAVECounter;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.teros.TEROSCounter;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.vist.VISTCounter;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Контроллер для консоли МСТ
 *
 * @author Maksim Shchelkonogov
 * 07.05.2024
 */
@ViewScoped
@Named("mctController")
public class MctConsoleController implements Serializable {

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

    private FtpCounterInfo counterForUpdate;
    private String remoteSelected;
    private StatData selectedStat;
    private LocalDateTime selectedDateTime;
    private Set<Config> config = new HashSet<>();
    private final Map<String, String[]> archiveData = new HashMap<>();
    private final List<MctConsoleController.ColumnModel> archiveColumnHeader = new ArrayList<>();
    private final List<AsyncModel> asyncData = new ArrayList<>();

    private final Map<String, MctFtpCounter> counters = Stream.of(new PlainCounter(), new SLAVECounter(),
                                                                    new SA94Counter(), new TEROSCounter(),
                                                                    new VISTCounter())
            .collect(Collectors.toMap(k -> k.getCounterInfo().getCounterName(),
                                        v -> v,
                                        (oldValue, newValue) -> oldValue,
                                        LinkedHashMap::new));

    @PostConstruct
    private void init() {
        remoteSelected = getRemotes().get(0);
        clearArchiveData();
    }

    private List<FtpCounterInfo> getInfos() {
        return counters.values().stream()
                .map(Counter::getCounterInfo)
                .filter(info -> info instanceof FtpCounterInfo)
                .map(info -> (FtpCounterInfo) info)
                .collect(Collectors.toList());
    }

    private FtpCounterInfo getInfo(String counter) {
        CounterInfo info = counters.get(counter).getCounterInfo();
        if (info instanceof FtpCounterInfo) {
            return (FtpCounterInfo) info;
        }
        return null;
    }

    /**
     * Получения статистики
     *
     * @return статистика
     */
    public List<StatData> getStatistic() {
        List<StatData> result = new ArrayList<>();
        getInfos().forEach(ftpCounterInfo ->
                result.addAll(ftpCounterInfo.getStatistic().entrySet().stream()
                                .filter(entry -> entry.getKey().getServer().equals(remoteSelected))
                                .map(Map.Entry::getValue)
                                .collect(Collectors.toList()))
        );
        result.sort(Comparator.comparing(statData -> statData.getObjectName() == null ? "" : statData.getObjectName(),
                                        new AlphaNumComparator()));
        return result;
    }

    /**
     * Запрос на конфигурацию счетчика
     */
    public void requestConfig() {
        config = counters.get(selectedStat.getCounter()).getConfig(selectedStat.getCounterName());
        bean.tryUploadConfigByCounterName(selectedStat.getCounter(), selectedStat.getCounterName(),
                                            remoteSelected, config);
    }

    public void clearConfig() {
        config.clear();
    }

    public void requestAsync() {
        MctFtpCounter counter = counters.get(selectedStat.getCounter());
        if (Objects.nonNull(counter) && (counter instanceof FtpCounterAsyncRequest)) {
            try {
                List<DataModel> dataModels = new ArrayList<>();
                for (Config configItem: counter.getConfig(selectedStat.getCounterName())) {
                    dataModels.add(DataModel.builder(configItem.getName(), 0, 0, 0).build());
                }
                ((FtpCounterAsyncRequest) counter).loadInstantData(dataModels, selectedStat.getCounterName());

                for (DataModel dataModel: dataModels) {
                    for (DataModel.ValueModel valueModel: dataModel.getData()) {
                        asyncData.add(new AsyncModel(dataModel.getParamName(), valueModel.getValue()));
                    }
                }

                PrimeFaces.current().executeScript("PF('asyncDataWidget').show();");
                PrimeFaces.current().ajax().update("asyncDataTable", "asyncDialogHeader");
            } catch (DasException e) {
                FacesContext.getCurrentInstance()
                        .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Ошибка", e.getMessage()));
                PrimeFaces.current().ajax().update("growl");
            }
        }
    }

    public void clearAsyncData() {
        asyncData.clear();
    }

    public void clearStatistic() {
        getInfos().forEach(info -> info.clearStatistic(statKey -> statKey.getServer().equals(remoteSelected)));
    }

    /**
     * Загрузка всех известных объектов счетчика
     */
    public void loadObjects(String counter) {
        if (getInfo(counter) != null) {
            Map<String, List<String>> counterObjects = bean.getCounterObjects(counter);
            bean.uploadCounterObjects(remoteSelected, counterObjects);
        }
    }

    public List<String> getCounters() {
        return getInfos().stream().map(FtpCounterInfo::getCounterName).collect(Collectors.toList());
    }

    /**
     * Изменение частоты опроса
     *
     * @param period частота опроса
     */
    public void changePeriodicity(String counter, String period) {
        if (getInfo(counter) != null) {
            singletonBean.getCounterProp(remoteSelected, counter).setPeriodicity(Periodicity.valueOf(period));
        }
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
    public String getPeriodicityMenuIcon(String counter, String period) {
        if (getInfo(counter) != null) {
            if (singletonBean.getCounterProp(remoteSelected, counter).getPeriodicity() == Periodicity.valueOf(period)) {
                return "pi pi-fw pi-circle-fill";
            } else {
                return "pi pi-fw pi-circle";
            }
        }

        return "pi pi-fw pi-circle";
    }

    /**
     * Загрузка исторических данных
     */
    public void loadArchiveData() {
        MctFtpCounter counter = counters.get(selectedStat.getCounter());
        DateTimeFormatter headerFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH");

        archiveColumnHeader.clear();

        for (int i = 0; i < 24; i++) {
            archiveColumnHeader.add(new MctConsoleController.ColumnModel(selectedDateTime.withHour(i).format(headerFormatter), "getValue()[" + i + "]"));
            try {
                for (String path: counter.getFileNames(selectedStat.getCounterName(), selectedDateTime.withHour(i))) {
                    ((FtpCounterExtension) counter).showData(path);
                    int finalI = i;
                    counter.getHist().forEach((key, value) -> {
                        if (archiveData.containsKey(key)) {
                            archiveData.get(key)[finalI] = value.getValue();
                        } else {
                            String[] values = new String[24];
                            values[finalI] = value.getValue();
                            archiveData.put(key, values);
                        }
                    });
                }
            } catch (IOException e) {
                logger.warn("Error load archive data", e);
            }
        }
    }

    public void clearArchiveData() {
        archiveData.clear();
        archiveColumnHeader.clear();
        for (int i = 0; i < 24; i++) {
            archiveColumnHeader.add(new MctConsoleController.ColumnModel(String.valueOf(i)));
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
                URLEncoder.encode("МСТ.xlsx", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            ExcelReport.generateReport(outputStream, "'МСТ'", getStatistic());
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
                URLEncoder.encode("МСТ.pdf", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            PdfReport.generateReport(outputStream, "'МСТ'", getStatistic());
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
    public int initCounterForUpdate(String counter) {
        counterForUpdate = getInfo(counter);
        return getThreadCount();
    }

    /**
     * Получение количества используемых потоков для опроса
     *
     * @return количество потоков
     */
    public int getThreadCount() {
        if (counterForUpdate != null) {
            return singletonBean.getCounterProp(remoteSelected, counterForUpdate.getCounterName()).getConcurrencyDepth();
        } else {
            return 0;
        }
    }

    /**
     * Установка количества используемых потоков для опроса
     *
     * @param threadCount количество потоков
     */
    public void setThreadCount(int threadCount) {
        if (counterForUpdate != null) {
            singletonBean.getCounterProp(remoteSelected, counterForUpdate.getCounterName()).setConcurrencyDepth(threadCount);
        }
    }

    public boolean isAsyncRequest() {
        if (selectedStat != null) {
            MctFtpCounter counter = counters.get(selectedStat.getCounter());
            return Objects.nonNull(counter) && (counter instanceof FtpCounterAsyncRequest);
        }
        return false;
    }

    public String getPrincipal() {
        return openIdContext.getClaimsJson().getString("name");
    }

    public boolean isAdmin() {
        return securityContext.isCallerInRole("admin");
    }

    public List<String> getRemotes() {
        List<String> result = new ArrayList<>(singletonBean.getRemotes().keySet());
        result.removeIf(remote -> !singletonBean.counterNameSet(remote).containsAll(counters.keySet()));
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

    public LocalDateTime getSelectedDateTime() {
        return LocalDateTime.now();
    }

    public LocalDateTime getMinDate() {
        return LocalDateTime.now().minusDays(45);
    }

    public void setSelectedDateTime(LocalDateTime selectedDateTime) {
        this.selectedDateTime = selectedDateTime;
    }

    public Set<Map.Entry<String, String[]>> getArchiveData() {
        return archiveData.entrySet();
    }

    public List<MctConsoleController.ColumnModel> getArchiveColumnHeader() {
        return archiveColumnHeader;
    }

    public Set<Config> getConfig() {
        return config;
    }

    public List<AsyncModel> getAsync() {
        return asyncData;
    }

    public String getCounterForUpdateName() {
        return counterForUpdate.getCounterName();
    }

    public static final class ColumnModel {

        private final String header;
        private String valueProperty;

        private ColumnModel(String header) {
            this.header = header;
        }

        private ColumnModel(String header, String valueProperty) {
            this.header = header;
            this.valueProperty = valueProperty;
        }

        public String getHeader() {
            return header;
        }

        public String getValueProperty() {
            return valueProperty;
        }
    }

    public static final class AsyncModel {

        private final String param;
        private final String value;

        private AsyncModel(String param, String value) {
            this.param = param;
            this.value = value;
        }

        public String getParam() {
            return param;
        }

        public String getValue() {
            return value;
        }
    }
}
