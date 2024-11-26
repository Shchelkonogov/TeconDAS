package ru.tecon.queryBasedDAS.counter.mfk;

import com.lowagie.text.*;
import com.lowagie.text.alignment.HorizontalAlignment;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author Maksim Shchelkonogov
 * 26.11.2024
 */
public class PdfTrafficReport {

    private static final String[] HEAD = {"№", "Имя объекта", "Имя прибора", "Суточный трафик"};

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /**
     * Метод формирует отчет по статистике
     *
     * @param outputStream поток в который записывается отчет
     * @param counterName название контроллера, для отображения в шапки
     * @param statistic данные для отчета
     */
    public static void generateReport(OutputStream outputStream, String counterName, List<MfkConsoleController.TrafficReportStatistic> statistic) {
        Document document = new Document(PageSize.A4.rotate());

        PdfWriter.getInstance(document, outputStream);

        document.open();

        FontFactory.register("font/Times New Roman.ttf", "TimeNewRoman");

        Paragraph p = new Paragraph("Статистика суточного трафика контроллеров " + counterName,
                FontFactory.getFont("TimeNewRoman", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 16, Font.BOLD));
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);

        Font font14 = FontFactory.getFont("TimeNewRoman", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 14, Font.BOLD);
        Font font12 = FontFactory.getFont("TimeNewRoman", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 12);

        Table dataTable = new Table(HEAD.length);
        dataTable.setWidths(new int[]{5, 35, 35, 25});
        dataTable.setWidth(100);
        dataTable.setPadding(3);
        dataTable.getDefaultCell().setBorderWidth(1);
        dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.CENTER);

        for (String head: HEAD) {
            dataTable.addCell(new Phrase(head, font14));
        }
        dataTable.endHeaders();

        int i = 1;
        for (MfkConsoleController.TrafficReportStatistic value: statistic) {
            dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.CENTER);
            dataTable.addCell(new Phrase(String.valueOf(i), font12));
            dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.LEFT);
            dataTable.addCell(new Phrase(value.getObjectName(), font12));
            dataTable.addCell(new Phrase(value.getCounterName(), font12));
            dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.CENTER);
            dataTable.addCell(new Phrase(value.getTraffic(), font12));
            i++;
        }
        document.add(dataTable);

        String createText = "Отчет сформирован " +
                LocalDateTime.now().format(FORMATTER);

        p = new Paragraph(createText,
                FontFactory.getFont("TimeNewRoman", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 12));
        p.setAlignment(Element.ALIGN_LEFT);
        document.add(p);

        document.close();
    }
}
