package ru.tecon.queryBasedDAS.counter.ftp.plain;

import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.ftp.FtpCounter;
import ru.tecon.queryBasedDAS.counter.ftp.model.CounterData;
import ru.tecon.uploaderService.model.DataModel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Разборщик счетчика МСТ20
 *
 * @author Maksim Shchelkonogov
 * 15.02.2024
 */
public class PlainCounter extends FtpCounter {

    private static final PlainInfo info = new PlainInfo();

    public PlainCounter() {
        super(info);
    }

    @Override
    public CounterInfo getCounterInfo() {
        return info;
    }

    @Override
    public Set<String> getConfig(String object) {
        return Stream
                .concat(
                        Stream.of(PlainConfig.values()).map(PlainConfig::getProperty),
                        Stream.of(PlainConfig.values())
                                .filter(plainConfig -> plainConfig.getRegister() != null)
                                .map(plainConfig -> plainConfig.getProperty() + ":Текущие данные")
                )
                .collect(Collectors.toSet());
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        params.removeIf(dataModel -> !Stream.of(PlainConfig.values())
                .map(PlainConfig::getProperty)
                .collect(Collectors.toSet())
                .contains(dataModel.getParamName()));
        super.loadData(params, objectName);
    }

    @Override
    public void readFile(InputStream in, String path) throws IOException, DasException {
        counterData.clear();

        try (BufferedInputStream inputStream = new BufferedInputStream(in)) {
            // read header
            byte[] buffer = new byte[14];
            if (inputStream.readNBytes(buffer, 0, buffer.length) == buffer.length) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
                if ((byteBuffer.getShort(0) != 6) || (byteBuffer.get(2) != 3)) {
                    throw new DasException("Неверный размер заголовка или версия протокола");
                }
                if (byteBuffer.get(5) != 0) {
                    throw new DasException("Результат обработки запроса выдал ошибку");
                }
                if (byteBuffer.getShort(10) != 11) {
                    throw new DasException("Неверное количество архивных записей");
                }
            } else {
                throw new DasException("Невозможно прочитать заголовок");
            }

            // read body
            for (int i = 0; i < 11; i++) {
                // read recordType
                byte[] recordType = inputStream.readNBytes(1);
                if (recordType.length != 1) {
                    throw new DasException("can't read recordType");
                }
                if ((i == 10) && (recordType[0] != 4)) {
                    throw new DasException("Error record type: " + recordType[0]);
                }

                // read record
                buffer = new byte[888];
                if (inputStream.readNBytes(buffer, 0, buffer.length) != buffer.length) {
                    throw new DasException("Error record size");
                }
                if (buffer[buffer.length - 1] != 10) {
                    throw new DasException("Ошибочное окончание записи");
                }

                // parse record (record 0-9 skip, record 10 parse)
                if (i == 10) {
                    // Проверка контрольной суммы архивной записи
                    int quality = 192;
                    byte[] crcBuffer = new byte[886];
                    crcBuffer[0] = 0x04;
                    System.arraycopy(buffer, 0, crcBuffer, 1, 885);
                    if ((((buffer[886] & 0xff) << 8) | (buffer[885] & 0xff)) != computeCrc16(crcBuffer)) {
                        quality = 0;
                    }

                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

                    long totalTime = Integer.toUnsignedLong(byteBuffer.getInt(7));
                    counterData.put(PlainConfig.TOTAL_TIME.getProperty(), new CounterData(totalTime, quality));
                    long offTime = Integer.toUnsignedLong(byteBuffer.getInt(11));
                    counterData.put(PlainConfig.OFF_TIME.getProperty(), new CounterData(offTime, quality));
                    long offTimeAccumulated = Integer.toUnsignedLong(byteBuffer.getInt(15));
                    counterData.put(PlainConfig.OFF_TIME_ACCUMULATED.getProperty(), new CounterData(offTimeAccumulated, quality));

                    int index = 19;
                    for (int j = 0; j < 4; j++) {
                        long stopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index));
                        counterData.put(STOP_TIME_G_ERROR_1[2 * j], new CounterData(stopTimeGError1, quality));
                        long stopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + 4));
                        counterData.put(STOP_TIME_G_ERROR_2[2 * j], new CounterData(stopTimeGError2, quality));
                        long stopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + 8));
                        counterData.put(STOP_TIME_G_ERROR_3[2 * j], new CounterData(stopTimeGError3, quality));
                        long workingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + 12));
                        counterData.put(WORKING_TIME_G[2 * j], new CounterData(workingTimeG, quality));
                        float waterVolume = byteBuffer.getFloat(index + 16);
                        counterData.put(WATER_VOLUME[2 * j], new CounterData(waterVolume, quality));

                        long stopTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index+ 20));
                        counterData.put(STOP_TIME_T[2 * j], new CounterData(stopTimeT, quality));
                        long workingTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index + 24));
                        counterData.put(WORKING_TIME_T[2 * j], new CounterData(workingTimeT, quality));
                        float waterTemper = byteBuffer.getFloat(index + 28);
                        counterData.put(WATER_TEMPER[2 * j], new CounterData(waterTemper, quality));

                        long stopTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index+ 32));
                        counterData.put(STOP_TIME_P[2 * j], new CounterData(stopTimeP, quality));
                        long workingTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index + 36));
                        counterData.put(WORKING_TIME_P[2 * j], new CounterData(workingTimeP, quality));
                        float waterPressure = byteBuffer.getFloat(index + 40);
                        counterData.put(WATER_PRESSURE[2 * j], new CounterData(waterPressure, quality));

                        long stopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 44));
                        counterData.put(STOP_TIME_MQ[2 * j], new CounterData(stopTimeMQ, quality));
                        long workingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 48));
                        counterData.put(WORKING_TIME_MQ[2 * j], new CounterData(workingTimeMQ, quality));
                        float waterWeight = byteBuffer.getFloat(index + 52);
                        counterData.put(WATER_WEIGHT[2 * j], new CounterData(waterWeight, quality));
                        float waterHeatAmount = byteBuffer.getFloat(index + 56);
                        counterData.put(WATER_HEAT_AMOUNT[2 * j], new CounterData(waterHeatAmount, quality));

                        long accumulatedStopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + 60));
                        counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_1[2 * j], new CounterData(accumulatedStopTimeGError1, quality));
                        long accumulatedStopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + 64));
                        counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_2[2 * j], new CounterData(accumulatedStopTimeGError2, quality));
                        long accumulatedStopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + 68));
                        counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_3[2 * j], new CounterData(accumulatedStopTimeGError3, quality));
                        long accumulatedWorkingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + 72));
                        counterData.put(ACCUMULATED_WORKING_TIME_G[2 * j], new CounterData(accumulatedWorkingTimeG, quality));
                        float waterAccumulated = byteBuffer.getFloat(index + 76);
                        counterData.put(WATER_ACCUMULATED[2 * j], new CounterData(waterAccumulated, quality));
                        long accumulatedStopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 80));
                        counterData.put(ACCUMULATED_STOP_TIME_MQ[2 * j], new CounterData(accumulatedStopTimeMQ, quality));
                        long accumulatedWorkingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 84));
                        counterData.put(ACCUMULATED_WORKING_TIME_MQ[2 * j], new CounterData(accumulatedWorkingTimeMQ, quality));
                        float waterMassAccumulated = byteBuffer.getFloat(index + 88);
                        counterData.put(WATER_MASS_ACCUMULATED[2 * j], new CounterData(waterMassAccumulated, quality));
                        float waterHeatAccumulated = byteBuffer.getFloat(index + 92);
                        counterData.put(WATER_HEAT_ACCUMULATED[2 * j], new CounterData(waterHeatAccumulated, quality));

                        stopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + 96));
                        counterData.put(STOP_TIME_G_ERROR_1[(2 * j) + 1], new CounterData(stopTimeGError1, quality));
                        stopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + 100));
                        counterData.put(STOP_TIME_G_ERROR_2[(2 * j) + 1], new CounterData(stopTimeGError2, quality));
                        stopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + 104));
                        counterData.put(STOP_TIME_G_ERROR_3[(2 * j) + 1], new CounterData(stopTimeGError3, quality));
                        workingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + 108));
                        counterData.put(WORKING_TIME_G[(2 * j) + 1], new CounterData(workingTimeG, quality));
                        waterVolume = byteBuffer.getFloat(index + 112);
                        counterData.put(WATER_VOLUME[(2 * j) + 1], new CounterData(waterVolume, quality));

                        stopTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index + 116));
                        counterData.put(STOP_TIME_T[(2 * j) + 1], new CounterData(stopTimeT, quality));
                        workingTimeT = Integer.toUnsignedLong(byteBuffer.getInt(index + 120));
                        counterData.put(WORKING_TIME_T[(2 * j) + 1], new CounterData(workingTimeT, quality));
                        waterTemper = byteBuffer.getFloat(index + 124);
                        counterData.put(WATER_TEMPER[(2 * j) + 1], new CounterData(waterTemper, quality));

                        stopTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index + 128));
                        counterData.put(STOP_TIME_P[(2 * j) + 1], new CounterData(stopTimeP, quality));
                        workingTimeP = Integer.toUnsignedLong(byteBuffer.getInt(index + 132));
                        counterData.put(WORKING_TIME_P[(2 * j) + 1], new CounterData(workingTimeP, quality));
                        waterPressure = byteBuffer.getFloat(index + 136);
                        counterData.put(WATER_PRESSURE[(2 * j) + 1], new CounterData(waterPressure, quality));

                        stopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 140));
                        counterData.put(STOP_TIME_MQ[(2 * j) + 1], new CounterData(stopTimeMQ, quality));
                        workingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 144));
                        counterData.put(WORKING_TIME_MQ[(2 * j) + 1], new CounterData(workingTimeMQ, quality));
                        waterWeight = byteBuffer.getFloat(index + 148);
                        counterData.put(WATER_WEIGHT[(2 * j) + 1], new CounterData(waterWeight, quality));
                        waterHeatAmount = byteBuffer.getFloat(index + 152);
                        counterData.put(WATER_HEAT_AMOUNT[(2 * j) + 1], new CounterData(waterHeatAmount, quality));

                        accumulatedStopTimeGError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + 156));
                        counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_1[(2 * j) + 1], new CounterData(accumulatedStopTimeGError1, quality));
                        accumulatedStopTimeGError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + 160));
                        counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_2[(2 * j) + 1], new CounterData(accumulatedStopTimeGError2, quality));
                        accumulatedStopTimeGError3 = Integer.toUnsignedLong(byteBuffer.getInt(index + 164));
                        counterData.put(ACCUMULATED_STOP_TIME_G_ERROR_3[(2 * j) + 1], new CounterData(accumulatedStopTimeGError3, quality));
                        accumulatedWorkingTimeG = Integer.toUnsignedLong(byteBuffer.getInt(index + 168));
                        counterData.put(ACCUMULATED_WORKING_TIME_G[(2 * j) + 1], new CounterData(accumulatedWorkingTimeG, quality));
                        waterAccumulated = byteBuffer.getFloat(index + 172);
                        counterData.put(WATER_ACCUMULATED[(2 * j) + 1], new CounterData(waterAccumulated, quality));
                        accumulatedStopTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 176));
                        counterData.put(ACCUMULATED_STOP_TIME_MQ[(2 * j) + 1], new CounterData(accumulatedStopTimeMQ, quality));
                        accumulatedWorkingTimeMQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 180));
                        counterData.put(ACCUMULATED_WORKING_TIME_MQ[(2 * j) + 1], new CounterData(accumulatedWorkingTimeMQ, quality));
                        waterMassAccumulated = byteBuffer.getFloat(index + 184);
                        counterData.put(WATER_MASS_ACCUMULATED[(2 * j) + 1], new CounterData(waterMassAccumulated, quality));
                        waterHeatAccumulated = byteBuffer.getFloat(index + 188);
                        counterData.put(WATER_HEAT_ACCUMULATED[(2 * j) + 1], new CounterData(waterHeatAccumulated, quality));

                        if (j < 3) {
                            long currentStopTimeError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + 192));
                            counterData.put(CURRENT_STOP_TIME_ERROR_1[j], new CounterData(currentStopTimeError1, quality));
                            long currentStopTimeError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + 196));
                            counterData.put(CURRENT_STOP_TIME_ERROR_2[j], new CounterData(currentStopTimeError2, quality));
                            long workingTimeQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 200));
                            counterData.put(WORKING_TIME_Q[j], new CounterData(workingTimeQ, quality));
                            float waterHeatZone = byteBuffer.getFloat(index + 204);
                            counterData.put(WATER_HEAT_ZONE[j], new CounterData(waterHeatZone, quality));

                            long accumulatedCurrentStopTimeError1 = Integer.toUnsignedLong(byteBuffer.getInt(index + 208));
                            counterData.put(ACCUMULATED_CURRENT_STOP_TIME_ERROR_1[j], new CounterData(accumulatedCurrentStopTimeError1, quality));
                            long accumulatedCurrentStopTimeError2 = Integer.toUnsignedLong(byteBuffer.getInt(index + 212));
                            counterData.put(ACCUMULATED_CURRENT_STOP_TIME_ERROR_2[j], new CounterData(accumulatedCurrentStopTimeError2, quality));
                            long accumulatedWorkingTimeQ = Integer.toUnsignedLong(byteBuffer.getInt(index + 216));
                            counterData.put(ACCUMULATED_WORKING_TIME_Q[j], new CounterData(accumulatedWorkingTimeQ, quality));
                            float accumulatedWaterHeatZone = byteBuffer.getFloat(index + 220);
                            counterData.put(ACCUMULATED_WATER_HEAT_ZONE[j], new CounterData(accumulatedWaterHeatZone, quality));
                            index += 224;
                        } else {
                            index += 192;
                        }
                    }
                }
            }
        }
    }

    private static final String[] STOP_TIME_G_ERROR_1 = {PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_0.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_1.getProperty(), PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_2.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_3.getProperty(), PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_4.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_5.getProperty(), PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_6.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_1_CHANEL_7.getProperty()};

    private static final String[] STOP_TIME_G_ERROR_2 = {PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_0.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_1.getProperty(), PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_2.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_3.getProperty(), PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_4.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_5.getProperty(), PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_6.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_2_CHANEL_7.getProperty()};

    private static final String[] STOP_TIME_G_ERROR_3 = {PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_0.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_1.getProperty(), PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_2.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_3.getProperty(), PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_4.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_5.getProperty(), PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_6.getProperty(),
            PlainConfig.STOP_TIME_G_ERROR_3_CHANEL_7.getProperty()};

    private static final String[] WORKING_TIME_G = {PlainConfig.WORKING_TIME_G_0.getProperty(),
            PlainConfig.WORKING_TIME_G_1.getProperty(), PlainConfig.WORKING_TIME_G_2.getProperty(),
            PlainConfig.WORKING_TIME_G_3.getProperty(), PlainConfig.WORKING_TIME_G_4.getProperty(),
            PlainConfig.WORKING_TIME_G_5.getProperty(), PlainConfig.WORKING_TIME_G_6.getProperty(),
            PlainConfig.WORKING_TIME_G_7.getProperty()};

    private static final String[] WATER_VOLUME = {PlainConfig.WATER_VOLUME0.getProperty(),
            PlainConfig.WATER_VOLUME1.getProperty(), PlainConfig.WATER_VOLUME2.getProperty(),
            PlainConfig.WATER_VOLUME3.getProperty(), PlainConfig.WATER_VOLUME4.getProperty(),
            PlainConfig.WATER_VOLUME5.getProperty(), PlainConfig.WATER_VOLUME6.getProperty(),
            PlainConfig.WATER_VOLUME7.getProperty()};

    private static final String[] STOP_TIME_T = {PlainConfig.STOP_TIME_T_0.getProperty(),
            PlainConfig.STOP_TIME_T_1.getProperty(), PlainConfig.STOP_TIME_T_2.getProperty(),
            PlainConfig.STOP_TIME_T_3.getProperty(), PlainConfig.STOP_TIME_T_4.getProperty(),
            PlainConfig.STOP_TIME_T_5.getProperty(), PlainConfig.STOP_TIME_T_6.getProperty(),
            PlainConfig.STOP_TIME_T_7.getProperty()};

    private static final String[] WORKING_TIME_T = {PlainConfig.WORKING_TIME_T_0.getProperty(),
            PlainConfig.WORKING_TIME_T_1.getProperty(), PlainConfig.WORKING_TIME_T_2.getProperty(),
            PlainConfig.WORKING_TIME_T_3.getProperty(), PlainConfig.WORKING_TIME_T_4.getProperty(),
            PlainConfig.WORKING_TIME_T_5.getProperty(), PlainConfig.WORKING_TIME_T_6.getProperty(),
            PlainConfig.WORKING_TIME_T_7.getProperty()};

    private static final String[] WATER_TEMPER = {PlainConfig.WATER_TEMPER0.getProperty(),
            PlainConfig.WATER_TEMPER1.getProperty(), PlainConfig.WATER_TEMPER2.getProperty(),
            PlainConfig.WATER_TEMPER3.getProperty(), PlainConfig.WATER_TEMPER4.getProperty(),
            PlainConfig.WATER_TEMPER5.getProperty(), PlainConfig.WATER_TEMPER6.getProperty(),
            PlainConfig.WATER_TEMPER7.getProperty()};

    private static final String[] STOP_TIME_P = {PlainConfig.STOP_TIME_P_0.getProperty(),
            PlainConfig.STOP_TIME_P_1.getProperty(), PlainConfig.STOP_TIME_P_2.getProperty(),
            PlainConfig.STOP_TIME_P_3.getProperty(), PlainConfig.STOP_TIME_P_4.getProperty(),
            PlainConfig.STOP_TIME_P_5.getProperty(), PlainConfig.STOP_TIME_P_6.getProperty(),
            PlainConfig.STOP_TIME_P_7.getProperty()};

    private static final String[] WORKING_TIME_P = {PlainConfig.WORKING_TIME_P_0.getProperty(),
            PlainConfig.WORKING_TIME_P_1.getProperty(), PlainConfig.WORKING_TIME_P_2.getProperty(),
            PlainConfig.WORKING_TIME_P_3.getProperty(), PlainConfig.WORKING_TIME_P_4.getProperty(),
            PlainConfig.WORKING_TIME_P_5.getProperty(), PlainConfig.WORKING_TIME_P_6.getProperty(),
            PlainConfig.WORKING_TIME_P_7.getProperty()};

    private static final String[] WATER_PRESSURE = {PlainConfig.WATER_PRESSURE0.getProperty(),
            PlainConfig.WATER_PRESSURE1.getProperty(), PlainConfig.WATER_PRESSURE2.getProperty(),
            PlainConfig.WATER_PRESSURE3.getProperty(), PlainConfig.WATER_PRESSURE4.getProperty(),
            PlainConfig.WATER_PRESSURE5.getProperty(), PlainConfig.WATER_PRESSURE6.getProperty(),
            PlainConfig.WATER_PRESSURE7.getProperty()};

    private static final String[] STOP_TIME_MQ = {PlainConfig.STOP_TIME_MQ_0.getProperty(),
            PlainConfig.STOP_TIME_MQ_1.getProperty(), PlainConfig.STOP_TIME_MQ_2.getProperty(),
            PlainConfig.STOP_TIME_MQ_3.getProperty(), PlainConfig.STOP_TIME_MQ_4.getProperty(),
            PlainConfig.STOP_TIME_MQ_5.getProperty(), PlainConfig.STOP_TIME_MQ_6.getProperty(),
            PlainConfig.STOP_TIME_MQ_7.getProperty()};

    private static final String[] WORKING_TIME_MQ = {PlainConfig.WORKING_TIME_MQ_0.getProperty(),
            PlainConfig.WORKING_TIME_MQ_1.getProperty(), PlainConfig.WORKING_TIME_MQ_2.getProperty(),
            PlainConfig.WORKING_TIME_MQ_3.getProperty(), PlainConfig.WORKING_TIME_MQ_4.getProperty(),
            PlainConfig.WORKING_TIME_MQ_5.getProperty(), PlainConfig.WORKING_TIME_MQ_6.getProperty(),
            PlainConfig.WORKING_TIME_MQ_7.getProperty()};

    private static final String[] WATER_WEIGHT = {PlainConfig.WATER_WEIGHT0.getProperty(),
            PlainConfig.WATER_WEIGHT1.getProperty(), PlainConfig.WATER_WEIGHT2.getProperty(),
            PlainConfig.WATER_WEIGHT3.getProperty(), PlainConfig.WATER_WEIGHT4.getProperty(),
            PlainConfig.WATER_WEIGHT5.getProperty(), PlainConfig.WATER_WEIGHT6.getProperty(),
            PlainConfig.WATER_WEIGHT7.getProperty()};

    private static final String[] WATER_HEAT_AMOUNT = {PlainConfig.WATER_HEAT_AMOUNT0.getProperty(),
            PlainConfig.WATER_HEAT_AMOUNT1.getProperty(), PlainConfig.WATER_HEAT_AMOUNT2.getProperty(),
            PlainConfig.WATER_HEAT_AMOUNT3.getProperty(), PlainConfig.WATER_HEAT_AMOUNT4.getProperty(),
            PlainConfig.WATER_HEAT_AMOUNT5.getProperty(), PlainConfig.WATER_HEAT_AMOUNT6.getProperty(),
            PlainConfig.WATER_HEAT_AMOUNT7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_G_ERROR_1 = {PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_0.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_1.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_2.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_3.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_4.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_5.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_6.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_G_ERROR_2 = {PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_0.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_1.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_2.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_3.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_4.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_5.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_6.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_G_ERROR_3 = {PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_0.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_1.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_2.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_3.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_4.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_5.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_6.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_7.getProperty()};

    private static final String[] ACCUMULATED_WORKING_TIME_G = {PlainConfig.ACCUMULATED_WORKING_TIME_G_0.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_G_1.getProperty(), PlainConfig.ACCUMULATED_WORKING_TIME_G_2.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_G_3.getProperty(), PlainConfig.ACCUMULATED_WORKING_TIME_G_4.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_G_5.getProperty(), PlainConfig.ACCUMULATED_WORKING_TIME_G_6.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_G_7.getProperty()};

    private static final String[] WATER_ACCUMULATED = {PlainConfig.WATER_ACCUMULATED0.getProperty(),
            PlainConfig.WATER_ACCUMULATED1.getProperty(), PlainConfig.WATER_ACCUMULATED2.getProperty(),
            PlainConfig.WATER_ACCUMULATED3.getProperty(), PlainConfig.WATER_ACCUMULATED4.getProperty(),
            PlainConfig.WATER_ACCUMULATED5.getProperty(), PlainConfig.WATER_ACCUMULATED6.getProperty(),
            PlainConfig.WATER_ACCUMULATED7.getProperty()};

    private static final String[] ACCUMULATED_STOP_TIME_MQ = {PlainConfig.ACCUMULATED_STOP_TIME_MQ_0.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_MQ_1.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_MQ_2.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_MQ_3.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_MQ_4.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_MQ_5.getProperty(), PlainConfig.ACCUMULATED_STOP_TIME_MQ_6.getProperty(),
            PlainConfig.ACCUMULATED_STOP_TIME_MQ_7.getProperty()};

    private static final String[] ACCUMULATED_WORKING_TIME_MQ = {PlainConfig.ACCUMULATED_WORKING_TIME_MQ_0.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_MQ_1.getProperty(), PlainConfig.ACCUMULATED_WORKING_TIME_MQ_2.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_MQ_3.getProperty(), PlainConfig.ACCUMULATED_WORKING_TIME_MQ_4.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_MQ_5.getProperty(), PlainConfig.ACCUMULATED_WORKING_TIME_MQ_6.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_MQ_7.getProperty()};

    private static final String[] WATER_MASS_ACCUMULATED = {PlainConfig.WATER_MASS_ACCUMULATED0.getProperty(),
            PlainConfig.WATER_MASS_ACCUMULATED1.getProperty(), PlainConfig.WATER_MASS_ACCUMULATED2.getProperty(),
            PlainConfig.WATER_MASS_ACCUMULATED3.getProperty(), PlainConfig.WATER_MASS_ACCUMULATED4.getProperty(),
            PlainConfig.WATER_MASS_ACCUMULATED5.getProperty(), PlainConfig.WATER_MASS_ACCUMULATED6.getProperty(),
            PlainConfig.WATER_MASS_ACCUMULATED7.getProperty()};

    private static final String[] WATER_HEAT_ACCUMULATED = {PlainConfig.WATER_HEAT_ACCUMULATED0.getProperty(),
            PlainConfig.WATER_HEAT_ACCUMULATED1.getProperty(), PlainConfig.WATER_HEAT_ACCUMULATED2.getProperty(),
            PlainConfig.WATER_HEAT_ACCUMULATED3.getProperty(), PlainConfig.WATER_HEAT_ACCUMULATED4.getProperty(),
            PlainConfig.WATER_HEAT_ACCUMULATED5.getProperty(), PlainConfig.WATER_HEAT_ACCUMULATED6.getProperty(),
            PlainConfig.WATER_HEAT_ACCUMULATED7.getProperty()};

    private static final String[] CURRENT_STOP_TIME_ERROR_1 = {PlainConfig.CURRENT_STOP_TIME_ERROR_1_ZONE_0.getProperty(),
            PlainConfig.CURRENT_STOP_TIME_ERROR_1_ZONE_1.getProperty(), PlainConfig.CURRENT_STOP_TIME_ERROR_1_ZONE_2.getProperty()};

    private static final String[] CURRENT_STOP_TIME_ERROR_2 = {PlainConfig.CURRENT_STOP_TIME_ERROR_2_ZONE_0.getProperty(),
            PlainConfig.CURRENT_STOP_TIME_ERROR_2_ZONE_1.getProperty(), PlainConfig.CURRENT_STOP_TIME_ERROR_2_ZONE_2.getProperty()};

    private static final String[] WORKING_TIME_Q = {PlainConfig.WORKING_TIME_Q_ZONE_0.getProperty(),
            PlainConfig.WORKING_TIME_Q_ZONE_1.getProperty(), PlainConfig.WORKING_TIME_Q_ZONE_2.getProperty()};

    private static final String[] WATER_HEAT_ZONE = {PlainConfig.WATER_HEAT_ZONE_0.getProperty(),
            PlainConfig.WATER_HEAT_ZONE_1.getProperty(), PlainConfig.WATER_HEAT_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_CURRENT_STOP_TIME_ERROR_1 = {PlainConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_0.getProperty(),
            PlainConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_1.getProperty(), PlainConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_CURRENT_STOP_TIME_ERROR_2 = {PlainConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_0.getProperty(),
            PlainConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_1.getProperty(), PlainConfig.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_WORKING_TIME_Q = {PlainConfig.ACCUMULATED_WORKING_TIME_Q_ZONE_0.getProperty(),
            PlainConfig.ACCUMULATED_WORKING_TIME_Q_ZONE_1.getProperty(), PlainConfig.ACCUMULATED_WORKING_TIME_Q_ZONE_2.getProperty()};

    private static final String[] ACCUMULATED_WATER_HEAT_ZONE = {PlainConfig.ACCUMULATED_WATER_HEAT_ZONE_0.getProperty(),
            PlainConfig.ACCUMULATED_WATER_HEAT_ZONE_1.getProperty(), PlainConfig.ACCUMULATED_WATER_HEAT_ZONE_2.getProperty()};
}
