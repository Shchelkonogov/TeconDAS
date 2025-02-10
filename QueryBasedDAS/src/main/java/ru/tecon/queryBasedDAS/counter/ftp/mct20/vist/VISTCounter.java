package ru.tecon.queryBasedDAS.counter.ftp.mct20.vist;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.MctFtpCounter;
import ru.tecon.queryBasedDAS.counter.ftp.model.CounterData;
import ru.tecon.uploaderService.model.Config;
import ru.tecon.uploaderService.model.DataModel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 * Разборщик счетчика МСТ20-VIST
 *
 * @author Maksim Shchelkonogov
 * 06.02.2024
 */
public class VISTCounter extends MctFtpCounter {

    private static final VISTInfo info = VISTInfo.getInstance();

    public VISTCounter() {
        super(info);
    }

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<Config> getConfig(String object) {
        return Stream.of(VISTConfig.values())
                .map(VISTConfig::getProperty)
                .map(Config::new)
                .collect(Collectors.toSet());
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        params.removeIf(dataModel -> !Stream.of(VISTConfig.values())
                                        .map(VISTConfig::getProperty)
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
                    "/" + matcher.group("path2") + "v" + dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH")));
            fileNames.add("/" + matcher.group("path1") +
                    "/" + matcher.group("path2") +
                    "/" + matcher.group("path2") + "h" + dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH")));
        }
        return fileNames;
    }

    @Override
    public void readFile(InputStream in, String path) throws IOException, DasException {
        counterData.clear();

        try (BufferedInputStream inputStream = new BufferedInputStream(in)) {
            byte[] buffer = inputStream.readAllBytes();

            if (buffer.length < 120) {
                throw new DasException("error size of file");
            }

            if (buffer[buffer.length - 1] != 10) {
                throw new DasException("Ошибочное окончание записи");
            }

            if ((((buffer[19] & 0x0f) >> 3) != 1) || (((buffer[19] & 0x10) >> 4) != 1)) {
                throw new DasException("Ошибка в valid");
            }

            if (computeCrc16(Arrays.copyOfRange(buffer, 2, buffer.length - 1)) !=
                    Short.toUnsignedInt(ByteBuffer.wrap(buffer, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort())) {
                throw new DasException("Ошибка в crc16");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

            counterData.put(VISTConfig.TIME_USPD.getProperty(), new CounterData(createDate(Arrays.copyOfRange(buffer, 2, 8))));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");
            String timeTs = LocalDateTime.parse(createDate(Arrays.copyOfRange(buffer, 8, 14)), formatter)
                    .plusHours(3)
                    .format(formatter);
            counterData.put(VISTConfig.TIME_TS.getProperty(), new CounterData(timeTs));

            int accuracyChanel1 = Byte.toUnsignedInt(byteBuffer.get(78));
            int accuracyChanel2 = Byte.toUnsignedInt(byteBuffer.get(79));
            int accuracyChanel3 = Byte.toUnsignedInt(byteBuffer.get(80));
            int accuracyChanelEnergy = Byte.toUnsignedInt(byteBuffer.get(81));

            double v1i = new BigDecimal(String.valueOf(byteBuffer.getInt(42))).movePointLeft(accuracyChanel1).doubleValue();
            counterData.put(VISTConfig.V1I.getProperty(), new CounterData(v1i));
            double v2i = new BigDecimal(String.valueOf(byteBuffer.getInt(46))).movePointLeft(accuracyChanel2).doubleValue();
            counterData.put(VISTConfig.V2I.getProperty(), new CounterData(v2i));
            double vpi = new BigDecimal(String.valueOf(byteBuffer.getInt(50))).movePointLeft(accuracyChanel3).doubleValue();
            counterData.put(VISTConfig.VPI.getProperty(), new CounterData(vpi));

            double g1i = new BigDecimal(String.valueOf(byteBuffer.getInt(54))).movePointLeft(accuracyChanel1).doubleValue();
            counterData.put(VISTConfig.G1I.getProperty(), new CounterData(g1i));
            double g2i = new BigDecimal(String.valueOf(byteBuffer.getInt(58))).movePointLeft(accuracyChanel2).doubleValue();
            counterData.put(VISTConfig.G2I.getProperty(), new CounterData(g2i));
            double gpi = new BigDecimal(String.valueOf(byteBuffer.getInt(62))).movePointLeft(accuracyChanel3).doubleValue();
            counterData.put(VISTConfig.GPI.getProperty(), new CounterData(gpi));

            double time0 = new BigDecimal(String.valueOf(Integer.toUnsignedLong(byteBuffer.getInt(66)))).movePointLeft(2).doubleValue();
            counterData.put(VISTConfig.TIME_0.getProperty(), new CounterData(time0));

            double pti = new BigDecimal(String.valueOf(byteBuffer.getLong(70))).movePointLeft(accuracyChanelEnergy).doubleValue();
            counterData.put(VISTConfig.PTI.getProperty(), new CounterData(pti));

            String parameterItems = new StringBuffer(Long.toBinaryString(Integer.toUnsignedLong(byteBuffer.getInt(26)))).reverse().toString();

            int increment = 0;
            // Если файл с "h", то это ВИСТ гидролинк и у него в архивной записи спереди стоит время (6 байт)
            // на эти 6 байт надо сделать смещение
            if (path.contains("h")) {
                increment = 6;
            }

            for (int i = 0; i < parameterItems.length(); i++) {
                switch (i) {
                    case 1:
                        if (parameterItems.charAt(i) == '1') {
                            double v1d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel1).doubleValue();
                            counterData.put(VISTConfig.V1D.getProperty(), new CounterData(v1d));
                            increment += 4;
                        }
                        break;
                    case 2:
                        if (parameterItems.charAt(i) == '1') {
                            double v2d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel2).doubleValue();
                            counterData.put(VISTConfig.V2D.getProperty(), new CounterData(v2d));
                            increment += 4;
                        }
                        break;
                    case 3:
                        if (parameterItems.charAt(i) == '1') {
                            double vpd = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel3).doubleValue();
                            counterData.put(VISTConfig.VPD.getProperty(), new CounterData(vpd));
                            increment += 4;
                        }
                        break;
                    case 4:
                        if (parameterItems.charAt(i) == '1') {
                            double g1d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel1).doubleValue();
                            counterData.put(VISTConfig.G1D.getProperty(), new CounterData(g1d));
                            increment += 4;
                        }
                        break;
                    case 5:
                        if (parameterItems.charAt(i) == '1') {
                            double g2d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel2).doubleValue();
                            counterData.put(VISTConfig.G2D.getProperty(), new CounterData(g2d));
                            increment += 4;
                        }
                        break;
                    case 6:
                        if (parameterItems.charAt(i) == '1') {
                            double gpd = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel3).doubleValue();
                            counterData.put(VISTConfig.GPD.getProperty(), new CounterData(gpd));
                            increment += 4;
                        }
                        break;
                    case 7:
                        if (parameterItems.charAt(i) == '1') {
                            float t1 = new BigDecimal(String.valueOf(byteBuffer.getShort(121 + increment))).movePointLeft(1).floatValue();
                            if (t1 == -1000) {
                                counterData.put(VISTConfig.T1.getProperty(), new CounterData(t1, 0));
                            } else {
                                counterData.put(VISTConfig.T1.getProperty(), new CounterData(t1));
                            }
                            increment += 2;
                        }
                        break;
                    case 8:
                        if (parameterItems.charAt(i) == '1') {
                            float t2 = new BigDecimal(String.valueOf(byteBuffer.getShort(121 + increment))).movePointLeft(1).floatValue();
                            if (t2 == -1000) {
                                counterData.put(VISTConfig.T2.getProperty(), new CounterData(t2, 0));
                            } else {
                                counterData.put(VISTConfig.T2.getProperty(), new CounterData(t2));
                            }
                            increment += 2;
                        }
                        break;
                    case 9:
                        if (parameterItems.charAt(i) == '1') {
                            float tp = new BigDecimal(String.valueOf(byteBuffer.getShort(121 + increment))).movePointLeft(1).floatValue();
                            if (tp == -1000) {
                                counterData.put(VISTConfig.TP.getProperty(), new CounterData(tp, 0));
                            } else {
                                counterData.put(VISTConfig.TP.getProperty(), new CounterData(tp));
                            }
                            increment += 2;
                        }
                        break;
                    case 10:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Средняя температура №4 (окружающая) [ºC] т/с"
                            increment += 2;
                        }
                        break;
                    case 11:
                        if (parameterItems.charAt(i) == '1') {
                            float p1 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(1).floatValue();
                            if (p1 == 0) {
                                counterData.put(VISTConfig.P1.getProperty(), new CounterData(p1, 0));
                            } else {
                                counterData.put(VISTConfig.P1.getProperty(), new CounterData(p1));
                            }
                            increment += 1;
                        }
                        break;
                    case 12:
                        if (parameterItems.charAt(i) == '1') {
                            float p2 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(1).floatValue();
                            if (p2 == 0) {
                                counterData.put(VISTConfig.P2.getProperty(), new CounterData(p2, 0));
                            } else {
                                counterData.put(VISTConfig.P2.getProperty(), new CounterData(p2));
                            }
                            increment += 1;
                        }
                        break;
                    case 13:
                        if (parameterItems.charAt(i) == '1') {
                            float pt = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(1).floatValue();
                            if (pt == 0) {
                                counterData.put(VISTConfig.PT.getProperty(), new CounterData(pt, 0));
                            } else {
                                counterData.put(VISTConfig.PT.getProperty(), new CounterData(pt));
                            }
                            increment += 1;
                        }
                        break;
                    case 14:
                        if (parameterItems.charAt(i) == '1') {
                            double ptd = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanelEnergy).doubleValue();
                            counterData.put(VISTConfig.PTD.getProperty(), new CounterData(ptd));
                            increment += 4;
                        }
                        break;
                    case 15:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Ошибки т/с"
                            increment += 4;
                        }
                        break;
                    case 16:
                        if (parameterItems.charAt(i) == '1') {
                            double time1 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            counterData.put(VISTConfig.TIME_1.getProperty(), new CounterData(time1));
                            increment += 1;
                        }
                        break;
                    case 17:
                        if (parameterItems.charAt(i) == '1') {
                            double time2 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            counterData.put(VISTConfig.TIME_2.getProperty(), new CounterData(time2));
                            increment += 1;
                        }
                        break;
                    case 18:
                        if (parameterItems.charAt(i) == '1') {
                            double time3 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            counterData.put(VISTConfig.TIME_3.getProperty(), new CounterData(time3));
                            increment += 1;
                        }
                        break;
                    case 19:
                        if (parameterItems.charAt(i) == '1') {
                            double time4 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            counterData.put(VISTConfig.TIME_4.getProperty(), new CounterData(time4));
                            increment += 1;
                        }
                        break;
                    case 20:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Зарезервировано"
                            increment += 3;
                        }
                        break;
                    case 0:
                    case 21:
                    case 22:
                        if (parameterItems.charAt(i) == '1') {
                            // 0: Не используем "Время наработки [ч] т/с"
                            // 21: Не используем "Общее учтенное время [ч] т/с (разделенные системы)"
                            // 22: Не используем "Время простоя [ч] т/с (датчик пустой трубы)"
                            increment += 1;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            throw new DasException("parse data Exception", e);
        }
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
