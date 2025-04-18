package ru.tecon.queryBasedDAS.counter.mfk;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.queryBasedDAS.counter.report.ExcelReport;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Maksim Shchelkonogov
 * 26.11.2024
 */
public class ExcelTrafficReport {

    private static final Logger logger = LoggerFactory.getLogger(ExcelReport.class);

    private static final String[] HEAD = {"№", "Имя объекта", "Имя прибора", "Суточный трафик"};

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /**
     * Метод формирует отчет по трафику
     *
     * @param outputStream поток в который записывается отчет
     * @param counterName название контроллера, для отображения в шапки
     * @param statistic данные для отчета
     */
    public static void generateReport(OutputStream outputStream, String counterName, List<MfkConsoleController.TrafficReportStatistic> statistic) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook()) {
            SXSSFSheet sheet = wb.createSheet("Статистика по трафику");

            Map<String, CellStyle> styleMap = createStyles(wb);

            sheet.setColumnWidth(0, 2 * 256);

            for (int i = 0; i < HEAD.length; i++) {
                sheet.trackColumnForAutoSizing(i + 1);
            }

            Row row = sheet.createRow(1);
            Cell cell = row.createCell(1);
            cell.setCellValue("Статистика суточного трафика контроллеров " + counterName);
            cell.setCellStyle(styleMap.get("header"));
            CellRangeAddress cellAddresses = new CellRangeAddress(1, 1, 1, HEAD.length);
            sheet.addMergedRegion(cellAddresses);

            createRow(sheet.createRow(3), styleMap.get("tableHeader"), HEAD);

            int index = 4;
            for (MfkConsoleController.TrafficReportStatistic value: statistic) {
                createRow(sheet.createRow(index),
                        new StyledValue(String.valueOf(index - 3), styleMap.get("cell")),
                        new StyledValue(value.getObjectName(), styleMap.get("cellLeft")),
                        new StyledValue(value.getCounterName(), styleMap.get("cellLeft")),
                        new StyledValue(value.getTraffic(), styleMap.get("cell")));

                index++;
            }

            for (int i = 0; i < HEAD.length; i++) {
                sheet.autoSizeColumn(i + 1);
            }

            sheet.setColumnWidth(2, 25 * 256);
            sheet.setColumnWidth(3, 40 * 256);

            String createText = "Отчет сформирован " +
                    LocalDateTime.now().format(FORMATTER);

            createRow(sheet.createRow(index + 1), styleMap.get("text"), createText);
            cellAddresses = new CellRangeAddress(index + 1, index + 1, 1, HEAD.length);
            sheet.addMergedRegion(cellAddresses);

            wb.write(outputStream);
        } catch (IOException e) {
            logger.warn("Error create report", e);
        }
    }

    private static void createRow(Row row, CellStyle style, String... items) {
        Cell cell;
        for (int i = 0; i < items.length; i++) {
            cell = row.createCell(i + 1);
            cell.setCellValue(items[i]);
            cell.setCellStyle(style);
        }
    }

    private static void createRow(Row row, StyledValue... items) {
        Cell cell;
        for (int i = 0; i < items.length; i++) {
            cell = row.createCell(i + 1);
            cell.setCellValue(items[i].value);
            cell.setCellStyle(items[i].style);
        }
    }

    private static Map<String, CellStyle> createStyles(Workbook wb) {
        Map<String, CellStyle> styles = new HashMap<>();

        XSSFFont font12 = (XSSFFont) wb.createFont();
        font12.setFontHeight(12);
        font12.setFontName("Times New Roman");

        XSSFFont font14 = (XSSFFont) wb.createFont();
        font14.setBold(true);
        font14.setFontHeight(14);
        font14.setFontName("Times New Roman");

        XSSFFont font16 = (XSSFFont) wb.createFont();
        font16.setBold(true);
        font16.setFontHeight(16);
        font16.setFontName("Times New Roman");

        CellStyle style = wb.createCellStyle();
        style.setFont(font16);
        style.setAlignment(HorizontalAlignment.CENTER);

        styles.put("header", style);

        style = wb.createCellStyle();
        style.setFont(font14);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);

        styles.put("tableHeader", style);

        style = wb.createCellStyle();
        style.setFont(font12);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);

        styles.put("cell", style);

        style = wb.createCellStyle();
        style.setFont(font12);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);

        styles.put("cellLeft", style);

        style = wb.createCellStyle();
        style.setFont(font12);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        styles.put("text", style);

        return styles;
    }

    private static final class StyledValue {

        private final String value;
        private final CellStyle style;

        private StyledValue(String value, CellStyle style) {
            this.value = value;
            this.style = style;
        }
    }
}
