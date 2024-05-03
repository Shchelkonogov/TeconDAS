package ru.tecon.queryBasedDAS.counter.ftp.eco;

import fish.payara.security.openid.api.OpenIdContext;
import org.slf4j.Logger;
import ru.tecon.queryBasedDAS.AlphaNumComparator;
import ru.tecon.queryBasedDAS.counter.Periodicity;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;
import ru.tecon.queryBasedDAS.counter.report.PdfReport;
import ru.tecon.queryBasedDAS.counter.report.ExcelReport;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounterExtension;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASSingletonBean;
import ru.tecon.queryBasedDAS.ejb.QueryBasedDASStatelessBean;
import ru.tecon.queryBasedDAS.ejb.RemoteEJBFactory;
import ru.tecon.uploaderService.ejb.UploaderServiceRemote;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.security.enterprise.SecurityContext;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Контроллер для консоли экомониторинга
 *
 * @author Maksim Shchelkonogov
 * 09.04.2024
 */
@ViewScoped
@Named("ecoController")
public class EcoConsoleController implements Serializable {

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

    @EJB
    private RemoteEJBFactory remoteEJBFactory;

    private String remoteSelected;
    private StatData selectedStat;
    private LocalDateTime selectedDateTime;
    private Set<String> config = new HashSet<>();
    private final Map<String, String[]> archiveData = new HashMap<>();
    private final List<ColumnModel> archiveColumnHeader = new ArrayList<>();

    private final EcoCounter counter = new EcoCounter();

    @PostConstruct
    private void init() {
        remoteSelected = getRemotes()[0];
        clearArchiveData();
    }

    /**
     * Получения статистики
     *
     * @return статистика
     */
    public List<StatData> getStatistic() {
        return EcoInfo.getInstance().getStatistic().entrySet().stream()
                .filter(entry -> entry.getKey().getServer().equals(remoteSelected))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(StatData::getCounterName, new AlphaNumComparator()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Запрос на конфигурацию счетчика
     */
    public void requestConfig() {
        config = counter.getConfig(selectedStat.getCounterName());

        try {
            UploaderServiceRemote remote = remoteEJBFactory.getUploadServiceRemote(remoteSelected);

            String counterObjectId = remote.getCounterObjectId(EcoInfo.getInstance().getCounterName(), selectedStat.getCounterName());
            if (counterObjectId != null) {
                remote.uploadConfig(config, counterObjectId, selectedStat.getCounterName());
            }

        } catch (NamingException e) {
            logger.warn("remote service {} unavailable", remoteSelected, e);
        }
    }

    public void clearConfig() {
        config.clear();
    }

    public void clearStatistic() {
        EcoInfo.getInstance().clearStatistic();
    }

    /**
     * Загрузка всех известных объектов счетчика
     */
    public void loadObjects() {
        Map<String, List<String>> counterObjects = bean.getCounterObjects(EcoInfo.getInstance().getCounterName());
        bean.uploadCounterObjects(remoteSelected, counterObjects);
    }

    /**
     * Изменение частоты опроса
     *
     * @param period частота опроса
     */
    public void changePeriodicity(String period) {
        singletonBean.setCounterProperty(EcoInfo.getInstance().getCounterName(), "periodicity", period);
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
        String periodicity = singletonBean.getCounterProperty(EcoInfo.getInstance().getCounterName(), "periodicity");
        if (periodicity == null) {
            periodicity = singletonBean.getProperty("periodicity");
        }
        return periodicity.equals(period) ? "pi pi-fw pi-circle-fill" : "pi pi-fw pi-circle";
    }

    /**
     * Загрузка исторических данных
     */
    public void loadArchiveData() {
        String stationNumber = selectedStat.getCounterName().replace("Станция ", "");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
        DateTimeFormatter headerFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        archiveColumnHeader.clear();

        for (int i = 0, j = 0; i < 60; i+=10, j++) {
            archiveColumnHeader.add(new ColumnModel(selectedDateTime.withMinute(i).format(headerFormatter), "getValue()[" + j + "]"));
            try {
                ((FtpCounterExtension) counter).showData("/[measure_529]_" +
                        "[" + stationNumber + "]_" +
                        "[" + selectedDateTime.withMinute(i).format(formatter) + "].xml");
                int finalJ = j;
                counter.getHist().forEach((key, value) -> {
                    if (archiveData.containsKey(key)) {
                        archiveData.get(key)[finalJ] = value.getValue();
                    } else {
                        String[] values = new String[6];
                        values[finalJ] = value.getValue();
                        archiveData.put(key, values);
                    }
                });
            } catch (IOException e) {
                logger.warn("Error load archive data", e);
            }
        }
    }

    public void clearArchiveData() {
        archiveData.clear();
        archiveColumnHeader.clear();
        for (int i = 0; i < 6; i++) {
            archiveColumnHeader.add(new ColumnModel(i + "0"));
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
                URLEncoder.encode("Экомониторинга.xlsx", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            ExcelReport.generateReport(outputStream, getStatistic());
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
                URLEncoder.encode("Экомониторинга.pdf", StandardCharsets.UTF_8) + "\"");
        ec.setResponseCharacterEncoding("UTF-8");

        try (OutputStream outputStream = ec.getResponseOutputStream()) {
            PdfReport.generateReport(outputStream, getStatistic());
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
        String depth = singletonBean.getCounterProperty(EcoInfo.getInstance().getCounterName(), "concurrencyDepth");
        if (depth == null) {
            depth = singletonBean.getProperty("concurrencyDepth");
        }
        return Integer.parseInt(depth);
    }

    /**
     * Установка количества используемых потоков для опроса
     *
     * @param threadCount количество потоков
     */
    public void setThreadCount(int threadCount) {
        singletonBean.setCounterProperty(EcoInfo.getInstance().getCounterName(), "concurrencyDepth", String.valueOf(threadCount));
    }

    public String getPrincipal() {
        return openIdContext.getClaimsJson().getString("name");
    }

    public boolean isAdmin() {
        return securityContext.isCallerInRole("admin");
    }

    public String[] getRemotes() {
        return singletonBean.getProperty("uploadServerNames").split(" ");
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
        return LocalDateTime.now().withMinute(0);
    }

    public LocalDateTime getMinDate() {
        return LocalDateTime.now().minusDays(7).withHour(1).withMinute(0);
    }

    public void setSelectedDateTime(LocalDateTime selectedDateTime) {
        this.selectedDateTime = selectedDateTime;
    }

    public Set<Map.Entry<String, String[]>> getArchiveData() {
        return archiveData.entrySet();
    }

    public List<ColumnModel> getArchiveColumnHeader() {
        return archiveColumnHeader;
    }

    public Set<String> getConfig() {
        return config;
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
}
