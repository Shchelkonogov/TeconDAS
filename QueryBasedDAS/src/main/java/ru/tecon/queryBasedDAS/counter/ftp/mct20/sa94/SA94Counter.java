package ru.tecon.queryBasedDAS.counter.ftp.mct20.sa94;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.MctFtpCounter;
import ru.tecon.queryBasedDAS.counter.ftp.model.CounterData;
import ru.tecon.uploaderService.model.DataModel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Разборщик счетчика МСТ20-SA94
 *
 * @author Maksim Shchelkonogov
 * 13.02.2024
 */
public class SA94Counter extends MctFtpCounter {

    private static final SA94Info info = SA94Info.getInstance();

    public SA94Counter() {
        super(info);
    }

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<String> getConfig(String object) {
        return Stream.of(SA94Config.values())
                .map(SA94Config::getProperty)
                .collect(Collectors.toSet());
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        params.removeIf(dataModel -> !Stream.of(SA94Config.values())
                                        .map(SA94Config::getProperty)
                                        .collect(Collectors.toSet())
                                        .contains(dataModel.getParamName()));
        super.loadData(params, objectName);
    }

    @Override
    public List<String> getFileNames(String counterName, LocalDateTime dateTime) {
        List<String> fileNames = super.getFileNames(counterName, dateTime);

        Pattern compile = Pattern.compile("^" + info.getCounterName() + "-(?<path2>(?<path1>\\d{2})\\d{2})$");
        Matcher matcher = compile.matcher(counterName);

        if (matcher.find()) {
            fileNames.clear();

            fileNames.add("/" + matcher.group("path1") +
                    "/" + matcher.group("path2") +
                    "/" + matcher.group("path2") + "s" + dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH")));
            fileNames.add("/" + matcher.group("path1") +
                    "/" + matcher.group("path2") +
                    "/" + matcher.group("path2") + "e" + dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH")));
        }
        return fileNames;
    }

    @Override
    public void readFile(InputStream in, String path) throws IOException, DasException {
        counterData.clear();

        if (path.contains("s")) {
            readFileNormal(in);
        } else {
            if (path.contains("e")) {
                readFileExtend(in);
            }
        }
    }

    private void readFileNormal(InputStream in) throws DasException, IOException {
        try (BufferedInputStream inputStream = new BufferedInputStream(in)) {
            byte[] buffer = inputStream.readAllBytes();

            checkData(buffer, 53, true);

            int quality = 192;
            if ((((buffer[1] & 0xff) << 8) | (buffer[0] & 0xff)) != computeCrc16(Arrays.copyOfRange(buffer, 2 , 52))) {
                quality = 0;
            }

            readPath(Arrays.copyOfRange(buffer, 20, 20 + 32), quality);

            readDates(buffer, quality);
        } catch (DateTimeParseException e) {
            throw new DasException("parse data Exception");
        }
    }

    private void readFileExtend(InputStream in) throws DasException, IOException {
        try (BufferedInputStream inputStream = new BufferedInputStream(in)) {
            byte[] buffer = inputStream.readAllBytes();

            checkData(buffer, 53 + 32, false);

            int quality = 192;
            if ((((buffer[1] & 0xff) << 8) | (buffer[0] & 0xff)) != computeCrc16(Arrays.copyOfRange(buffer, 2, 52 + 32))) {
                quality = 0;
            }

            readPath(Arrays.copyOfRange(buffer, 20, 20 + 32), quality);

            readDates(buffer, quality);

            float p1 = readFloat(Arrays.copyOfRange(buffer, 52, 56));
            counterData.put(SA94Config.P1.getProperty(), new CounterData(p1, quality));
            float p2 = readFloat(Arrays.copyOfRange(buffer, 56, 60));
            counterData.put(SA94Config.P2.getProperty(), new CounterData(p2, quality));
            float v1d = readFloat(Arrays.copyOfRange(buffer, 60, 64));
            counterData.put(SA94Config.V1D.getProperty(), new CounterData(v1d, quality));
            float v2d = readFloat(Arrays.copyOfRange(buffer, 64, 78));
            counterData.put(SA94Config.V2D.getProperty(), new CounterData(v2d, quality));
            float v1i = readFloat(Arrays.copyOfRange(buffer, 68, 72));
            counterData.put(SA94Config.V1I.getProperty(), new CounterData(v1i, quality));
            float v2i = readFloat(Arrays.copyOfRange(buffer, 72, 76));
            counterData.put(SA94Config.V2I.getProperty(), new CounterData(v2i, quality));

            float time0 = readFloat(Arrays.copyOfRange(buffer, 76, 80));
            counterData.put(SA94Config.TIME_0.getProperty(), new CounterData(time0, quality));
        } catch (DateTimeParseException e) {
            throw new DasException("parse data Exception");
        }
    }

    private void checkData(byte[] buffer, int bufferSize, boolean standardArchive) throws DasException {
        if (buffer.length < bufferSize) {
            throw new DasException("checkData Ошибка в данных");
        }

        if (((buffer[18] & 0x01) != 1) || (((buffer[18] & 0x02) >> 1) != 1)) {
            throw new DasException("Ошибка в valid SA94 archive");
        }
        if (standardArchive) {
            if (((buffer[18] & 0x0f) >> 3) != 1) {
                throw new DasException("Ошибка в valid standard SA94 archive");
            }
        } else {
            if (((buffer[18] & 0x10) >> 4) != 1) {
                throw new DasException("Ошибка в valid extended SA94 archive");
            }
        }

        if ((buffer[bufferSize - 1] & 0xff) != 10) {
            throw new DasException("checkData Ошибочное окончание записи");
        }
    }

    private void readDates(byte[] buffer, int quality) {
        counterData.put(SA94Config.TIME_USPD.getProperty(), new CounterData(createDate(Arrays.copyOfRange(buffer, 2, 8)), quality));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");
        String timeTs = LocalDateTime
                .parse(createDate(Arrays.copyOfRange(buffer, 8, 14)), formatter)
                .plusHours(3)
                .format(formatter);
        counterData.put(SA94Config.TIME_TS.getProperty(), new CounterData(timeTs, quality));
    }

    private void readPath(byte[] buffer, int quality) {

        //
        // ВНИМАНИЕ
        //
        // Расчет t1 и t2 ведутся по-разному в зависимости от версии sa94.
        // Температуры t1 и t2 могут храниться или в виде float 4 байта или в short 2 байта и надо умножить на 0.01
        // На float 4 байта есть информация в документации
        // На short 2 байта умноженное на 0.01 информация от Арсения из компании Тепловизор
        // Изначально использовался вариант на 2 байта, т.к. значения совпадают
        // 14.03.2024 заметили объекты с 4 байтами. Реализовать пока не получается, т.к. нет информации про тип sa94.
        //

        float g1i = readFloat(Arrays.copyOfRange(buffer, 8, 12));
        counterData.put(SA94Config.G1I.getProperty(), new CounterData(g1i, quality));
        float g2i = readFloat(Arrays.copyOfRange(buffer, 12, 16));
        counterData.put(SA94Config.G2I.getProperty(), new CounterData(g2i, quality));
        float t1 = new BigDecimal(String.valueOf(((buffer[16] & 0xff) << 8) | (buffer[17] & 0xff)))
                .multiply(new BigDecimal("0.01")).floatValue();
//        float t1 = readFloat(Arrays.copyOfRange(buffer, 16, 20));
        counterData.put(SA94Config.T1.getProperty(), new CounterData(t1, quality));
        float t2 = new BigDecimal(String.valueOf(((buffer[18] & 0xff) << 8) | (buffer[19] & 0xff)))
                .multiply(new BigDecimal("0.01")).floatValue();
//        float t2 = readFloat(Arrays.copyOfRange(buffer, 20, 24));
        counterData.put(SA94Config.T2.getProperty(), new CounterData(t2, quality));
        float pti = readFloat(Arrays.copyOfRange(buffer, 28, 32));
        counterData.put(SA94Config.PTI.getProperty(), new CounterData(pti, quality));
    }

    private float readFloat(byte[] buffer) {
        if (((buffer[0] & 0xff) >> 1) == 0) {
            buffer[0] = 0x00;
        } else {
            buffer[0] -= 0x02;
        }

        return Float.intBitsToFloat(
                ((((buffer[0] & 0xff) >> 1) | (buffer[1] & 0x80)) << 24) |
                        (((buffer[1] & 0x7f)| ((buffer[0] & 0x01) << 7)) << 16) |
                        ((buffer[2] & 0xff) << 8) |
                        (buffer[3] & 0xff));
    }

    private String createDate(byte[] buffer) {
        return createStringValue(buffer[2]) +
                createStringValue(buffer[1]) +
                ((buffer[0] & 0xff) < 95 ? ("20" + createStringValue(buffer[0])) : ("19" + createStringValue(buffer[0]))) +
                createStringValue(buffer[3]) +
                createStringValue(buffer[4]) +
                createStringValue(buffer[5]);
    }

    private String createStringValue(byte b) {
        return (b & 0xff) < 10 ? ("0" + (b & 0xff)) : String.valueOf(b & 0xff);
    }
}
