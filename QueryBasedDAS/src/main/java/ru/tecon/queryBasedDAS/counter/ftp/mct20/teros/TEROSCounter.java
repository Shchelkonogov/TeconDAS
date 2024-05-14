package ru.tecon.queryBasedDAS.counter.ftp.mct20.teros;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.MctFtpCounter;
import ru.tecon.queryBasedDAS.counter.ftp.model.CounterData;
import ru.tecon.uploaderService.model.DataModel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Разборщик счетчика МСТ20-TEROS
 *
 * @author Maksim Shchelkonogov
 * 12.02.2024
 */
public class TEROSCounter extends MctFtpCounter {

    private static final TEROSInfo info = TEROSInfo.getInstance();

    public TEROSCounter() {
        super(info);
    }

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<String> getConfig(String object) {
        return Stream.of(TEROSConfig.values())
                .map(TEROSConfig::getProperty)
                .collect(Collectors.toSet());
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        params.removeIf(dataModel -> !Stream.of(TEROSConfig.values())
                                        .map(TEROSConfig::getProperty)
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
                    "/" + matcher.group("path2") + "t" + dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH")));
        }
        return fileNames;
    }

    @Override
    public void readFile(InputStream in, String path) throws IOException, DasException {
        counterData.clear();

        try (BufferedInputStream inputStream = new BufferedInputStream(in)) {
            byte[] buffer = inputStream.readAllBytes();

            if (buffer.length != 537) {
                throw new DasException("error size of file");
            }

            if (buffer[buffer.length - 1] != 10) {
                throw new DasException("Ошибочное окончание записи");
            }

            if ((((buffer[18] & 0x0f) >> 3) != 1) || (((buffer[18] & 0x10) >> 4) != 1)) {
                throw new DasException("Ошибка в valid");
            }

            if ((buffer[19] & 0x0f) != 0) {
                throw new DasException("В файле присутствуют ошибки");
            }

            if (computeCrc16(Arrays.copyOfRange(buffer, 2, buffer.length - 1)) !=
                    Short.toUnsignedInt(ByteBuffer.wrap(buffer, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort())) {
                throw new DasException("Ошибка в crc16");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            counterData.put(TEROSConfig.TIME_USPD.getProperty(), new CounterData(createDate(Arrays.copyOfRange(buffer, 2, 8))));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");
            String timeTs = LocalDateTime.parse(createDate(Arrays.copyOfRange(buffer, 8, 14)), formatter)
                    .plusHours(3)
                    .format(formatter);
            counterData.put(TEROSConfig.TIME_TS.getProperty(), new CounterData(timeTs));

            counterData.put(TEROSConfig.T1.getProperty(), new CounterData(byteBuffer.getFloat(48)));
            counterData.put(TEROSConfig.T2.getProperty(), new CounterData(byteBuffer.getFloat(52)));
            counterData.put(TEROSConfig.TP.getProperty(), new CounterData(byteBuffer.getFloat(56)));
            counterData.put(TEROSConfig.P1.getProperty(), new CounterData(byteBuffer.getFloat(60)));
            counterData.put(TEROSConfig.P2.getProperty(), new CounterData(byteBuffer.getFloat(64)));
            counterData.put(TEROSConfig.PT.getProperty(), new CounterData(byteBuffer.getFloat(68)));

            counterData.put(TEROSConfig.G1I.getProperty(), new CounterData(byteBuffer.getFloat(292)));
            counterData.put(TEROSConfig.G2I.getProperty(), new CounterData(byteBuffer.getFloat(296)));
            counterData.put(TEROSConfig.GPI.getProperty(), new CounterData(byteBuffer.getFloat(300)));

            counterData.put(TEROSConfig.V1I.getProperty(), new CounterData(byteBuffer.getFloat(304)));
            counterData.put(TEROSConfig.V2I.getProperty(), new CounterData(byteBuffer.getFloat(308)));
            counterData.put(TEROSConfig.VPI.getProperty(), new CounterData(byteBuffer.getFloat(317)));

            counterData.put(TEROSConfig.PTI.getProperty(), new CounterData(byteBuffer.getFloat(313)));

            counterData.put(TEROSConfig.TIME_0.getProperty(), new CounterData(byteBuffer.getFloat(321)));
            counterData.put(TEROSConfig.TIME_1.getProperty(), new CounterData(byteBuffer.getFloat(329)));
            counterData.put(TEROSConfig.TIME_2.getProperty(), new CounterData(byteBuffer.getFloat(337)));
            counterData.put(TEROSConfig.TIME_3.getProperty(), new CounterData(byteBuffer.getFloat(333)));
            counterData.put(TEROSConfig.TIME_4.getProperty(), new CounterData(byteBuffer.getFloat(325)));
        } catch (DateTimeParseException e) {
            throw new DasException("parse data Exception");
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
