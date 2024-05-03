package ru.tecon.queryBasedDAS.counter.report;

import com.lowagie.text.*;
import com.lowagie.text.alignment.HorizontalAlignment;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import ru.tecon.queryBasedDAS.counter.statistic.StatData;

import java.io.OutputStream;
import java.util.List;

/**
 * Класс для формирования excel отчета по данным статистики
 *
 * @author Maksim Shchelkonogov
 * 03.05.2024
 */
public class PdfReport {

    private static final String[] HEAD = {"№", "Имя объекта", "Имя прибора", "Дата последних данных",
            "Диапазон запрашиваемых измерений"};

    /**
     * Метод формирует отчет по статистике
     *
     * @param outputStream поток в который записывается отчет
     * @param statisticList данные для отчета
     */
    public static void generateReport(OutputStream outputStream, List<StatData> statisticList) {
        Document document = new Document(PageSize.A4.rotate());

        PdfWriter.getInstance(document, outputStream);

        document.open();

        FontFactory.register("font/Times New Roman.ttf", "TimeNewRoman");

        Paragraph p = new Paragraph("Статистика работы контроллеров Экомониторинга",
                FontFactory.getFont("TimeNewRoman", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 16, Font.BOLD));
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);

        Font font14 = FontFactory.getFont("TimeNewRoman", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 14, Font.BOLD);
        Font font12 = FontFactory.getFont("TimeNewRoman", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 12);

        Table dataTable = new Table(5);
        dataTable.setWidths(new int[]{5, 20, 20, 25, 30});
        dataTable.setWidth(100);
        dataTable.setPadding(3);
        dataTable.getDefaultCell().setBorderWidth(1);
        dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.CENTER);

        for (String head: HEAD) {
            dataTable.addCell(new Phrase(head, font14));
        }
        dataTable.endHeaders();

        int i = 1;
        for (StatData statData: statisticList) {
            dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.CENTER);
            dataTable.addCell(new Phrase(String.valueOf(i), font12));
            dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.LEFT);
            dataTable.addCell(new Phrase(statData.getObjectName(), font12));
            dataTable.getDefaultCell().setHorizontalAlignment(HorizontalAlignment.CENTER);
            dataTable.addCell(new Phrase(statData.getCounterName(), font12));
            dataTable.addCell(new Phrase(statData.getLastDataTimeString(), font12));
            dataTable.addCell(new Phrase(statData.getRequestedRange(), font12));
            i++;
        }
        document.add(dataTable);

        document.close();
    }
}
