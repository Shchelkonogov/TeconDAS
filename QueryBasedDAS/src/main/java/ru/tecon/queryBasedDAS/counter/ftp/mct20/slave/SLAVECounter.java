package ru.tecon.queryBasedDAS.counter.ftp.mct20.slave;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.ftp.mct20.FtpCounterWithAsyncRequest;
import ru.tecon.queryBasedDAS.counter.ftp.model.CounterData;
import ru.tecon.uploaderService.model.Config;
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
 * Разборщик счетчика МСТ20-SLAVE
 *
 * @author Maksim Shchelkonogov
 * 14.02.2024
 */
public class SLAVECounter extends FtpCounterWithAsyncRequest {

    private static final SLAVEInfo info = SLAVEInfo.getInstance();

    public SLAVECounter() {
        super(info);
    }

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<Config> getConfig(String object) {
        return Stream
                .concat(
                        Stream.of(SLAVEConfig.values()).map(SLAVEConfig::getProperty),
                        Stream.of(SLAVEConfig.values())
                                .filter(slaveConfig -> slaveConfig.getRegister() != null)
                                .map(slaveConfig -> slaveConfig.getProperty() + ":Текущие данные")
                )
                .map(Config::new)
                .collect(Collectors.toSet());
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        params.removeIf(dataModel -> !Stream.of(SLAVEConfig.values())
                                        .map(SLAVEConfig::getProperty)
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
                    "/" + matcher.group("path2") + "b" + dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH")));
        }
        return fileNames;
    }

    @Override
    public void readFile(InputStream in, String path) throws IOException, DasException {
        counterData.clear();

        try (BufferedInputStream inputStream = new BufferedInputStream(in)) {
            byte[] buffer = inputStream.readAllBytes();
            if (buffer.length != 913) {
                throw new DasException("error read file");
            }

            if (buffer[buffer.length - 1] != 10) {
                throw new DasException("Ошибочное окончание записи");
            }

            if (((buffer[18] & 0x10) >> 4) != 1) {
                throw new DasException("Ошибка в valid");
            }

            if (computeCrc16(Arrays.copyOfRange(buffer, 2, buffer.length - 1)) !=
                    Short.toUnsignedInt(ByteBuffer.wrap(buffer, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort())) {
                throw new DasException("Ошибка в crc16");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            if (byteBuffer.getShort(22) != 888) {
                throw new DasException("Ошибка в размере архивной записи");
            }

            // Проверка контрольной суммы архивной записи
            int quality = 192;
            String recordCrc = Integer.toHexString(
                    Short.toUnsignedInt(
                            ByteBuffer.wrap(buffer, 910, 2)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .getShort()
                    )
            );

            String recordCrcCalc = computeCrc16Hex(
                    Arrays.copyOfRange(buffer, 24, buffer.length - 3)
            );

            if (!recordCrc.equals(recordCrcCalc)) {
                quality = 0;
            }

            counterData.put(SLAVEConfig.TIME_USPD.getProperty(), new CounterData(createDate(Arrays.copyOfRange(buffer, 2, 8)), quality));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");
            String timeTs = LocalDateTime.parse(createDate(Arrays.copyOfRange(buffer, 8, 14)), formatter)
                    .plusHours(3)
                    .format(formatter);
            counterData.put(SLAVEConfig.TIME_TS.getProperty(), new CounterData(timeTs, quality));

            int headerSize = 25;

            long totalTime = Integer.toUnsignedLong(byteBuffer.getInt(7 + headerSize));
            counterData.put(SLAVEConfig.TOTAL_TIME.getProperty(), new CounterData(totalTime, quality));
            long offTime = Integer.toUnsignedLong(byteBuffer.getInt(11 + headerSize));
            counterData.put(SLAVEConfig.OFF_TIME.getProperty(), new CounterData(offTime, quality));
            long offTimeAccumulated = Integer.toUnsignedLong(byteBuffer.getInt(15 + headerSize));
            counterData.put(SLAVEConfig.OFF_TIME_ACCUMULATED.getProperty(), new CounterData(offTimeAccumulated, quality));

            int index = 19;
            for (int i = 0; i < 4; i++) {
                long stopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize));
                counterData.put(STOP_TIME_G_ERROR_1[2 * i], new CounterData(stopTimeGError1, quality));
                long stopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 4));
                counterData.put(STOP_TIME_G_ERROR_2[2 * i], new CounterData(stopTimeGError2, quality));
                long stopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 8));
                counterData.put(STOP_TIME_G_ERROR_3[2 * i], new CounterData(stopTimeGError3, quality));
                long workingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 12));
                counterData.put(WORKING_TIME_G[2 * i], new CounterData(workingTimeG, quality));
                float waterVolume = byteBuffer.getFloat(index + headerSize + 16);
                counterData.put(WATER_VOLUME[2 * i], new CounterData(waterVolume, quality));

                long stopTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 20));
                counterData.put(STOP_TIME_T[2 * i], new CounterData(stopTimeT, quality));
                long workingTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 24));
                counterData.put(WORKING_TIME_T[2 * i], new CounterData(workingTimeT, quality));
                float waterTemper = byteBuffer.getFloat(index + headerSize + 28);
                counterData.put(WATER_TEMPER[2 * i], new CounterData(waterTemper, quality));

                long stopTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 32));
                counterData.put(STOP_TIME_P[2 * i], new CounterData(stopTimeP, quality));
                long workingTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 36));
                counterData.put(WORKING_TIME_P[2 * i], new CounterData(workingTimeP, quality));
                float waterPressure = byteBuffer.getFloat(index + headerSize + 40);
                counterData.put(WATER_PRESSURE[2 * i], new CounterData(waterPressure, quality));

                long stopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 44));
                counterData.put(STOP_TIME_MQ[2 * i], new CounterData(stopTimeMQ, quality));
                long workingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 48));
                counterData.put(WORKING_TIME_MQ[2 * i], new CounterData(workingTimeMQ, quality));
                float waterWeight = byteBuffer.getFloat(index + headerSize + 52);
                counterData.put(WATER_WEIGHT[2 * i], new CounterData(waterWeight, quality));
                float waterHeatAmount = byteBuffer.getFloat(index + headerSize + 56);
                counterData.put(WATER_HEAT_AMOUNT[2 * i], new CounterData(waterHeatAmount, quality));

                long accumulatedStopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 60));
                counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_1[2 * i], new CounterData(accumulatedStopTimeGError1, quality));
                long accumulatedStopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 64));
                counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_2[2 * i], new CounterData(accumulatedStopTimeGError2, quality));
                long accumulatedStopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 68));
                counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_3[2 * i], new CounterData(accumulatedStopTimeGError3, quality));
                long accumulatedWorkingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 72));
                counterData.put(ACCUMULATED_WORKING_TIME_G[2 * i], new CounterData(accumulatedWorkingTimeG, quality));
                float waterAccumulated = byteBuffer.getFloat(index + headerSize + 76);
                counterData.put(WATER_ACCUMULATED[2 * i], new CounterData(waterAccumulated, quality));
                long accumulatedStopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 80));
                counterData.put(ACCUMULATED_STOP_TIME_MQ[2 * i], new CounterData(accumulatedStopTimeMQ, quality));
                long accumulatedWorkingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 84));
                counterData.put(ACCUMULATED_WORKING_TIME_MQ[2 * i], new CounterData(accumulatedWorkingTimeMQ, quality));
                float waterMassAccumulated = byteBuffer.getFloat(index + headerSize + 88);
                counterData.put(WATER_MASS_ACCUMULATED[2 * i], new CounterData(waterMassAccumulated, quality));
                float waterHeatAccumulated = byteBuffer.getFloat(index + headerSize + 92);
                counterData.put(WATER_HEAT_ACCUMULATED[2 * i], new CounterData(waterHeatAccumulated, quality));

                stopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 96));
                counterData.put(STOP_TIME_G_ERROR_1[(2 * i) + 1], new CounterData(stopTimeGError1, quality));
                stopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 100));
                counterData.put(STOP_TIME_G_ERROR_2[(2 * i) + 1], new CounterData(stopTimeGError2, quality));
                stopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 104));
                counterData.put(STOP_TIME_G_ERROR_3[(2 * i) + 1], new CounterData(stopTimeGError3, quality));
                workingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 108));
                counterData.put(WORKING_TIME_G[(2 * i) + 1], new CounterData(workingTimeG, quality));
                waterVolume = byteBuffer.getFloat(index + headerSize + 112);
                counterData.put(WATER_VOLUME[(2 * i) + 1], new CounterData(waterVolume, quality));

                stopTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 116));
                counterData.put(STOP_TIME_T[(2 * i) + 1], new CounterData(stopTimeT, quality));
                workingTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 120));
                counterData.put(WORKING_TIME_T[(2 * i) + 1], new CounterData(workingTimeT, quality));
                waterTemper = byteBuffer.getFloat(index + headerSize + 124);
                counterData.put(WATER_TEMPER[(2 * i) + 1], new CounterData(waterTemper, quality));

                stopTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 128));
                counterData.put(STOP_TIME_P[(2 * i) + 1], new CounterData(stopTimeP, quality));
                workingTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 132));
                counterData.put(WORKING_TIME_P[(2 * i) + 1], new CounterData(workingTimeP, quality));
                waterPressure = byteBuffer.getFloat(index + headerSize + 136);
                counterData.put(WATER_PRESSURE[(2 * i) + 1], new CounterData(waterPressure, quality));

                stopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 140));
                counterData.put(STOP_TIME_MQ[(2 * i) + 1], new CounterData(stopTimeMQ, quality));
                workingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 144));
                counterData.put(WORKING_TIME_MQ[(2 * i) + 1], new CounterData(workingTimeMQ, quality));
                waterWeight = byteBuffer.getFloat(index + headerSize + 148);
                counterData.put(WATER_WEIGHT[(2 * i) + 1], new CounterData(waterWeight, quality));
                waterHeatAmount = byteBuffer.getFloat(index + headerSize + 152);
                counterData.put(WATER_HEAT_AMOUNT[(2 * i) + 1], new CounterData(waterHeatAmount, quality));

                accumulatedStopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 156));
                counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_1[(2 * i) + 1], new CounterData(accumulatedStopTimeGError1, quality));
                accumulatedStopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 160));
                counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_2[(2 * i) + 1], new CounterData(accumulatedStopTimeGError2, quality));
                accumulatedStopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 164));
                counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_3[(2 * i) + 1], new CounterData(accumulatedStopTimeGError3, quality));
                accumulatedWorkingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 168));
                counterData.put(ACCUMULATED_WORKING_TIME_G[(2 * i) + 1], new CounterData(accumulatedWorkingTimeG, quality));
                waterAccumulated = byteBuffer.getFloat(index + headerSize + 172);
                counterData.put(WATER_ACCUMULATED[(2 * i) + 1], new CounterData(waterAccumulated, quality));
                accumulatedStopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 176));
                counterData.put(ACCUMULATED_STOP_TIME_MQ[(2 * i) + 1], new CounterData(accumulatedStopTimeMQ, quality));
                accumulatedWorkingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 180));
                counterData.put(ACCUMULATED_WORKING_TIME_MQ[(2 * i) + 1], new CounterData(accumulatedWorkingTimeMQ, quality));
                waterMassAccumulated = byteBuffer.getFloat(index + headerSize + 184);
                counterData.put(WATER_MASS_ACCUMULATED[(2 * i) + 1], new CounterData(waterMassAccumulated, quality));
                waterHeatAccumulated = byteBuffer.getFloat(index + headerSize + 188);
                counterData.put(WATER_HEAT_ACCUMULATED[(2 * i) + 1], new CounterData(waterHeatAccumulated, quality));

                if (i < 3) {
                    long currentStopTimeError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 192));
                    counterData.put(CURRENT_STOP_TIME_ERROR_1[i], new CounterData(currentStopTimeError1, quality));
                    long currentStopTimeError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 196));
                    counterData.put(CURRENT_STOP_TIME_ERROR_2[i], new CounterData(currentStopTimeError2, quality));
                    long workingTimeQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 200));
                    counterData.put(WORKING_TIME_Q[i], new CounterData(workingTimeQ, quality));
                    float waterHeatZone = byteBuffer.getFloat(index + headerSize + 204);
                    counterData.put(WATER_HEAT_ZONE[i], new CounterData(waterHeatZone, quality));

                    long accumulatedCurrentStopTimeError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 208));
                    counterData.put(ACCUMULATED_CURRENT_STOP_TIME_ERROR_1[i], new CounterData(accumulatedCurrentStopTimeError1, quality));
                    long accumulatedCurrentStopTimeError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 212));
                    counterData.put(ACCUMULATED_CURRENT_STOP_TIME_ERROR_2[i], new CounterData(accumulatedCurrentStopTimeError2, quality));
                    long accumulatedWorkingTimeQ = Integer.toUnsignedLong(byteBuffer.getInt(index + headerSize + 216));
                    counterData.put(ACCUMULATED_WORKING_TIME_Q[i], new CounterData(accumulatedWorkingTimeQ, quality));
                    float accumulatedWaterHeatZone = byteBuffer.getFloat(index + headerSize + 220);
                    counterData.put(ACCUMULATED_WATER_HEAT_ZONE[i], new CounterData(accumulatedWaterHeatZone, quality));
                    index += 224;
                } else {
                    index += 192;
                }
            }
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

    /**
     * Подсчет контрольной суммы crc16 modbus
     * @param data данные для расчета crc
     * @return расчитанная crc в hex
     */
    public String computeCrc16Hex(byte[] data) {
        return Integer.toHexString(computeCrc16(data));
    }

    @Override
    public void loadInstantData(List<DataModel> params, String objectName) throws DasException {
        // Убираем не мгновенные параметры
        params.removeIf(dataModel -> !Stream.of(SLAVEConfig.values())
                .filter(SLAVEConfig::isInstant)
                .map(slaveConfig -> slaveConfig.getProperty() + ":Текущие данные")
                .collect(Collectors.toSet())
                .contains(dataModel.getParamName()));

        super.loadInstantData(params, objectName);
    }

    @Override
    protected String getPropRegister(String propName) throws DasException {
        return Stream.of(SLAVEConfig.values())
                .filter(slaveConfig -> slaveConfig.getProperty().equals(propName))
                .findFirst()
                .orElseThrow(() -> new DasException("Неожиданный параметр " + propName))
                .getRegister();
    }

    private static final String[] STOP_TIME_G_ERROR_1 = {SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_0.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_1.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_2.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_3.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_4.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_5.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_6.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_1_CHANEL_7.getProperty()};

    private static final String[] STOP_TIME_G_ERROR_2 = {SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_0.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_1.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_2.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_3.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_4.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_5.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_6.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_2_CHANEL_7.getProperty()};

    private static final String[] STOP_TIME_G_ERROR_3 = {SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_0.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_1.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_2.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_3.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_4.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_5.getProperty(), SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_6.getProperty(),
            SLAVEConfig.STOP_TIME_G_ERROR_3_CHANEL_7.getProperty()};

    private static final String[] WORKING_TIME_G = {SLAVEConfig.WORKING_TIME_G_0.getProperty(),
            SLAVEConfig.WORKING_TIME_G_1.getProperty(), SLAVEConfig.WORKING_TIME_G_2.getProperty(),
            SLAVEConfig.WORKING_TIME_G_3.getProperty(), SLAVEConfig.WORKING_TIME_G_4.getProperty(),
            SLAVEConfig.WORKING_TIME_G_5.getProperty(), SLAVEConfig.WORKING_TIME_G_6.getProperty(),
            SLAVEConfig.WORKING_TIME_G_7.getProperty()};

    private static final String[] WATER_VOLUME = {SLAVEConfig.WATER_VOLUME0.getProperty(),
            SLAVEConfig.WATER_VOLUME1.getProperty(), SLAVEConfig.WATER_VOLUME2.getProperty(),
            SLAVEConfig.WATER_VOLUME3.getProperty(), SLAVEConfig.WATER_VOLUME4.getProperty(),
            SLAVEConfig.WATER_VOLUME5.getProperty(), SLAVEConfig.WATER_VOLUME6.getProperty(),
            SLAVEConfig.WATER_VOLUME7.getProperty()};

    private static final String[] STOP_TIME_T = {SLAVEConfig.STOP_TIME_T_0.getProperty(),
            SLAVEConfig.STOP_TIME_T_1.getProperty(), SLAVEConfig.STOP_TIME_T_2.getProperty(),
            SLAVEConfig.STOP_TIME_T_3.getProperty(), SLAVEConfig.STOP_TIME_T_4.getProperty(),
            SLAVEConfig.STOP_TIME_T_5.getProperty(), SLAVEConfig.STOP_TIME_T_6.getProperty(),
            SLAVEConfig.STOP_TIME_T_7.getProperty()};

    private static final String[] WORKING_TIME_T = {SLAVEConfig.WORKING_TIME_T_0.getProperty(),
            SLAVEConfig.WORKING_TIME_T_1.getProperty(), SLAVEConfig.WORKING_TIME_T_2.getProperty(),
            SLAVEConfig.WORKING_TIME_T_3.getProperty(), SLAVEConfig.WORKING_TIME_T_4.getProperty(),
            SLAVEConfig.WORKING_TIME_T_5.getProperty(), SLAVEConfig.WORKING_TIME_T_6.getProperty(),
            SLAVEConfig.WORKING_TIME_T_7.getProperty()};

    private static final String[] WATER_TEMPER = {SLAVEConfig.WATER_TEMPER0.getProperty(),
            SLAVEConfig.WATER_TEMPER1.getProperty(), SLAVEConfig.WATER_TEMPER2.getProperty(),
            SLAVEConfig.WATER_TEMPER3.getProperty(), SLAVEConfig.WATER_TEMPER4.getProperty(),
            SLAVEConfig.WATER_TEMPER5.getProperty(), SLAVEConfig.WATER_TEMPER6.getProperty(),
            SLAVEConfig.WATER_TEMPER7.getProperty()};

    private static final String[] STOP_TIME_P = {SLAVEConfig.STOP_TIME_P_0.getProperty(),
            SLAVEConfig.STOP_TIME_P_1.getProperty(), SLAVEConfig.STOP_TIME_P_2.getProperty(),
            SLAVEConfig.STOP_TIME_P_3.getProperty(), SLAVEConfig.STOP_TIME_P_4.getProperty(),
            SLAVEConfig.STOP_TIME_P_5.getProperty(), SLAVEConfig.STOP_TIME_P_6.getProperty(),
            SLAVEConfig.STOP_TIME_P_7.getProperty()};

    private static final String[] WORKING_TIME_P = {SLAVEConfig.WORKING_TIME_P_0.getProperty(),
            SLAVEConfig.WORKING_TIME_P_1.getProperty(), SLAVEConfig.WORKING_TIME_P_2.getProperty(),
            SLAVEConfig.WORKING_TIME_P_3.getProperty(), SLAVEConfig.WORKING_TIME_P_4.getProperty(),
            SLAVEConfig.WORKING_TIME_P_5.getProperty(), SLAVEConfig.WORKING_TIME_P_6.getProperty(),
            SLAVEConfig.WORKING_TIME_P_7.getProperty()};

    private static final String[] WATER_PRESSURE = {SLAVEConfig.WATER_PRESSURE0.getProperty(),
            SLAVEConfig.WATER_PRESSURE1.getProperty(), SLAVEConfig.WATER_PRESSURE2.getProperty(),
            SLAVEConfig.WATER_PRESSURE3.getProperty(), SLAVEConfig.WATER_PRESSURE4.getProperty(),
            SLAVEConfig.WATER_PRESSURE5.getProperty(), SLAVEConfig.WATER_PRESSURE6.getProperty(),
            SLAVEConfig.WATER_PRESSURE7.getProperty()};

    private static final String[] STOP_TIME_MQ = {SLAVEConfig.STOP_TIME_MQ_0.getProperty(),
            SLAVEConfig.STOP_TIME_MQ_1.getProperty(), SLAVEConfig.STOP_TIME_MQ_2.getProperty(),
            SLAVEConfig.STOP_TIME_MQ_3.getProperty(), SLAVEConfig.STOP_TIME_MQ_4.getProperty(),
            SLAVEConfig.STOP_TIME_MQ_5.getProperty(), SLAVEConfig.STOP_TIME_MQ_6.getProperty(),
            SLAVEConfig.STOP_TIME_MQ_7.getProperty()};

    private static final String[] WORKING_TIME_MQ = {SLAVEConfig.WORKING_TIME_MQ_0.getProperty(),
            SLAVEConfig.WORKING_TIME_MQ_1.getProperty(), SLAVEConfig.WORKING_TIME_MQ_2.getProperty(),
            SLAVEConfig.WORKING_TIME_MQ_3.getProperty(), SLAVEConfig.WORKING_TIME_MQ_4.getProperty(),
            SLAVEConfig.WORKING_TIME_MQ_5.getProperty(), SLAVEConfig.WORKING_TIME_MQ_6.getProperty(),
            SLAVEConfig.WORKING_TIME_MQ_7.getProperty()};

    private static final String[] WATER_WEIGHT = {SLAVEConfig.WATER_WEIGHT0.getProperty(),
            SLAVEConfig.WATER_WEIGHT1.getProperty(), SLAVEConfig.WATER_WEIGHT2.getProperty(),
            SLAVEConfig.WATER_WEIGHT3.getProperty(), SLAVEConfig.WATER_WEIGHT4.getProperty(),
            SLAVEConfig.WATER_WEIGHT5.getProperty(), SLAVEConfig.WATER_WEIGHT6.getProperty(),
            SLAVEConfig.WATER_WEIGHT7.getProperty()};

    private static final String[] WATER_HEAT_AMOUNT = {SLAVEConfig.WATER_HEAT_AMOUNT0.getProperty(),
            SLAVEConfig.WATER_HEAT_AMOUNT1.getProperty(), SLAVEConfig.WATER_HEAT_AMOUNT2.getProperty(),
            SLAVEConfig.WATER_HEAT_AMOUNT3.getProperty(), SLAVEConfig.WATER_HEAT_AMOUNT4.getProperty(),
            SLAVEConfig.WATER_HEAT_AMOUNT5.getProperty(), SLAVEConfig.WATER_HEAT_AMOUNT6.getProperty(),
            SLAVEConfig.WATER_HEAT_AMOUNT7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_G_ERROR_1 = {SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_0.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_1.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_2.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_3.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_4.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_5.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_6.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_G_ERROR_2 = {SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_0.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_1.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_2.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_3.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_4.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_5.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_6.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_G_ERROR_3 = {SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_0.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_1.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_2.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_3.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_4.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_5.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_6.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_7.getProperty()};

    private static final String[] ACCUMULATED_WORKING_TIME_G = {SLAVEConfig.ACCUMULATED_WORKING_TIME_G_0.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_G_1.getProperty(), SLAVEConfig.ACCUMULATED_WORKING_TIME_G_2.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_G_3.getProperty(), SLAVEConfig.ACCUMULATED_WORKING_TIME_G_4.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_G_5.getProperty(), SLAVEConfig.ACCUMULATED_WORKING_TIME_G_6.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_G_7.getProperty()};

    private static final String[] WATER_ACCUMULATED = {SLAVEConfig.WATER_ACCUMULATED0.getProperty(),
            SLAVEConfig.WATER_ACCUMULATED1.getProperty(), SLAVEConfig.WATER_ACCUMULATED2.getProperty(),
            SLAVEConfig.WATER_ACCUMULATED3.getProperty(), SLAVEConfig.WATER_ACCUMULATED4.getProperty(),
            SLAVEConfig.WATER_ACCUMULATED5.getProperty(), SLAVEConfig.WATER_ACCUMULATED6.getProperty(),
            SLAVEConfig.WATER_ACCUMULATED7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_MQ = {SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_0.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_1.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_2.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_3.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_4.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_5.getProperty(), SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_6.getProperty(),
            SLAVEConfig.ACCUMULATED_STOP_TIME_MQ_7.getProperty()};

    private static final String[] ACCUMULATED_WORKING_TIME_MQ = {SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_0.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_1.getProperty(), SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_2.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_3.getProperty(), SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_4.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_5.getProperty(), SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_6.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_MQ_7.getProperty()};

    private static final String[] WATER_MASS_ACCUMULATED = {SLAVEConfig.WATER_MASS_ACCUMULATED0.getProperty(),
            SLAVEConfig.WATER_MASS_ACCUMULATED1.getProperty(), SLAVEConfig.WATER_MASS_ACCUMULATED2.getProperty(),
            SLAVEConfig.WATER_MASS_ACCUMULATED3.getProperty(), SLAVEConfig.WATER_MASS_ACCUMULATED4.getProperty(),
            SLAVEConfig.WATER_MASS_ACCUMULATED5.getProperty(), SLAVEConfig.WATER_MASS_ACCUMULATED6.getProperty(),
            SLAVEConfig.WATER_MASS_ACCUMULATED7.getProperty()};

    private static final String[] WATER_HEAT_ACCUMULATED = {SLAVEConfig.WATER_HEAT_ACCUMULATED0.getProperty(),
            SLAVEConfig.WATER_HEAT_ACCUMULATED1.getProperty(), SLAVEConfig.WATER_HEAT_ACCUMULATED2.getProperty(),
            SLAVEConfig.WATER_HEAT_ACCUMULATED3.getProperty(), SLAVEConfig.WATER_HEAT_ACCUMULATED4.getProperty(),
            SLAVEConfig.WATER_HEAT_ACCUMULATED5.getProperty(), SLAVEConfig.WATER_HEAT_ACCUMULATED6.getProperty(),
            SLAVEConfig.WATER_HEAT_ACCUMULATED7.getProperty()};

    private static final String[] CURRENT_STOP_TIME_ERROR_1 = {SLAVEConfig.CURRENT_STOP_TIME_ERROR_1_ZONE_0.getProperty(),
            SLAVEConfig.CURRENT_STOP_TIME_ERROR_1_ZONE_1.getProperty(), SLAVEConfig.CURRENT_STOP_TIME_ERROR_1_ZONE_2.getProperty()};

    private static final String[] CURRENT_STOP_TIME_ERROR_2 = {SLAVEConfig.CURRENT_STOP_TIME_ERROR_2_ZONE_0.getProperty(),
            SLAVEConfig.CURRENT_STOP_TIME_ERROR_2_ZONE_1.getProperty(), SLAVEConfig.CURRENT_STOP_TIME_ERROR_2_ZONE_2.getProperty()};

    private static final String[] WORKING_TIME_Q = {SLAVEConfig.WORKING_TIME_Q_ZONE_0.getProperty(),
            SLAVEConfig.WORKING_TIME_Q_ZONE_1.getProperty(), SLAVEConfig.WORKING_TIME_Q_ZONE_2.getProperty()};

    private static final String[] WATER_HEAT_ZONE = {SLAVEConfig.WATER_HEAT_ZONE_0.getProperty(),
            SLAVEConfig.WATER_HEAT_ZONE_1.getProperty(), SLAVEConfig.WATER_HEAT_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_CURRENT_STOP_TIME_ERROR_1 = {SLAVEConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_0.getProperty(),
            SLAVEConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_1.getProperty(), SLAVEConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_CURRENT_STOP_TIME_ERROR_2 = {SLAVEConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_0.getProperty(),
            SLAVEConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_1.getProperty(), SLAVEConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_WORKING_TIME_Q = {SLAVEConfig.ACCUMULATED_WORKING_TIME_Q_ZONE_0.getProperty(),
            SLAVEConfig.ACCUMULATED_WORKING_TIME_Q_ZONE_1.getProperty(), SLAVEConfig.ACCUMULATED_WORKING_TIME_Q_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_WATER_HEAT_ZONE = {SLAVEConfig.ACCUMULATED_WATER_HEAT_ZONE_0.getProperty(),
            SLAVEConfig.ACCUMULATED_WATER_HEAT_ZONE_1.getProperty(), SLAVEConfig.ACCUMULATED_WATER_HEAT_ZONE_2.getProperty()};
}
