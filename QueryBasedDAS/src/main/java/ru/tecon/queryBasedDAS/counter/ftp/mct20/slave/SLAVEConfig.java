package ru.tecon.queryBasedDAS.counter.ftp.mct20.slave;

/**
 * Конфигурация счетчика МСТ20-SLAVE
 *
 * @author Maksim Shchelkonogov
 * 14.02.2024
 */
public enum SLAVEConfig {

    TIME_TS("Время ТС"),
    TIME_USPD("Время УСПД"),

    TOTAL_TIME("Общее время работы"),

    WATER_VOLUME0("Объем воды за время канал 0", "100/16/0/float"),
    WATER_VOLUME1("Объем воды за время канал 1", "100/16/2/float"),
    WATER_VOLUME2("Объем воды за время канал 2", "100/16/4/float"),
    WATER_VOLUME3("Объем воды за время канал 3", "100/16/6/float"),
    WATER_VOLUME4("Объем воды за время канал 4", "100/16/8/float"),
    WATER_VOLUME5("Объем воды за время канал 5", "100/16/10/float"),
    WATER_VOLUME6("Объем воды за время канал 6", "100/16/12/float"),
    WATER_VOLUME7("Объем воды за время канал 7", "100/16/14/float"),

    WATER_WEIGHT0("Масса воды за время канал 0", "120/16/0/float"),
    WATER_WEIGHT1("Масса воды за время канал 1", "120/16/2/float"),
    WATER_WEIGHT2("Масса воды за время канал 2", "120/16/4/float"),
    WATER_WEIGHT3("Масса воды за время канал 3", "120/16/6/float"),
    WATER_WEIGHT4("Масса воды за время канал 4", "120/16/8/float"),
    WATER_WEIGHT5("Масса воды за время канал 5", "120/16/10/float"),
    WATER_WEIGHT6("Масса воды за время канал 6", "120/16/12/float"),
    WATER_WEIGHT7("Масса воды за время канал 7", "120/16/14/float"),

    WATER_TEMPER0("Температура за время канал 0", "140/16/0/float"),
    WATER_TEMPER1("Температура за время канал 1", "140/16/2/float"),
    WATER_TEMPER2("Температура за время канал 2", "140/16/4/float"),
    WATER_TEMPER3("Температура за время канал 3", "140/16/6/float"),
    WATER_TEMPER4("Температура за время канал 4", "140/16/8/float"),
    WATER_TEMPER5("Температура за время канал 5", "140/16/10/float"),
    WATER_TEMPER6("Температура за время канал 6", "140/16/12/float"),
    WATER_TEMPER7("Температура за время канал 7", "140/16/14/float"),

    WATER_PRESSURE0("Давление используемое в рассчетах канал 0"),
    WATER_PRESSURE1("Давление используемое в рассчетах канал 1"),
    WATER_PRESSURE2("Давление используемое в рассчетах канал 2"),
    WATER_PRESSURE3("Давление используемое в рассчетах канал 3"),
    WATER_PRESSURE4("Давление используемое в рассчетах канал 4"),
    WATER_PRESSURE5("Давление используемое в рассчетах канал 5"),
    WATER_PRESSURE6("Давление используемое в рассчетах канал 6"),
    WATER_PRESSURE7("Давление используемое в рассчетах канал 7"),

    WATER_HEAT_AMOUNT0("Количество тепла за время канал 0", "180/16/0/float"),
    WATER_HEAT_AMOUNT1("Количество тепла за время канал 1", "180/16/2/float"),
    WATER_HEAT_AMOUNT2("Количество тепла за время канал 2", "180/16/4/float"),
    WATER_HEAT_AMOUNT3("Количество тепла за время канал 3", "180/16/6/float"),
    WATER_HEAT_AMOUNT4("Количество тепла за время канал 4", "180/16/8/float"),
    WATER_HEAT_AMOUNT5("Количество тепла за время канал 5", "180/16/10/float"),
    WATER_HEAT_AMOUNT6("Количество тепла за время канал 6", "180/16/12/float"),
    WATER_HEAT_AMOUNT7("Количество тепла за время канал 7", "180/16/14/float"),

    WATER_ACCUMULATED0("Объем воды накопленный канал 0", "300/16/0/float"),
    WATER_ACCUMULATED1("Объем воды накопленный канал 1", "300/16/2/float"),
    WATER_ACCUMULATED2("Объем воды накопленный канал 2", "300/16/4/float"),
    WATER_ACCUMULATED3("Объем воды накопленный канал 3", "300/16/6/float"),
    WATER_ACCUMULATED4("Объем воды накопленный канал 4", "300/16/8/float"),
    WATER_ACCUMULATED5("Объем воды накопленный канал 5", "300/16/10/float"),
    WATER_ACCUMULATED6("Объем воды накопленный канал 6", "300/16/12/float"),
    WATER_ACCUMULATED7("Объем воды накопленный канал 7", "300/16/14/float"),

    WATER_MASS_ACCUMULATED0("Масса воды накопленная канала 0", "320/16/0/float"),
    WATER_MASS_ACCUMULATED1("Масса воды накопленная канала 1", "320/16/2/float"),
    WATER_MASS_ACCUMULATED2("Масса воды накопленная канала 2", "320/16/4/float"),
    WATER_MASS_ACCUMULATED3("Масса воды накопленная канала 3", "320/16/6/float"),
    WATER_MASS_ACCUMULATED4("Масса воды накопленная канала 4", "320/16/8/float"),
    WATER_MASS_ACCUMULATED5("Масса воды накопленная канала 5", "320/16/10/float"),
    WATER_MASS_ACCUMULATED6("Масса воды накопленная канала 6", "320/16/12/float"),
    WATER_MASS_ACCUMULATED7("Масса воды накопленная канала 7", "320/16/14/float"),

    WATER_HEAT_ACCUMULATED0("Количество тепла накопленное канал 0", "380/16/0/float"),
    WATER_HEAT_ACCUMULATED1("Количество тепла накопленное канал 1", "380/16/2/float"),
    WATER_HEAT_ACCUMULATED2("Количество тепла накопленное канал 2", "380/16/4/float"),
    WATER_HEAT_ACCUMULATED3("Количество тепла накопленное канал 3", "380/16/6/float"),
    WATER_HEAT_ACCUMULATED4("Количество тепла накопленное канал 4", "380/16/8/float"),
    WATER_HEAT_ACCUMULATED5("Количество тепла накопленное канал 5", "380/16/10/float"),
    WATER_HEAT_ACCUMULATED6("Количество тепла накопленное канал 6", "380/16/12/float"),
    WATER_HEAT_ACCUMULATED7("Количество тепла накопленное канал 7", "380/16/14/float"),

    OFF_TIME("Время выключенного состояния"),
    OFF_TIME_ACCUMULATED("Накопленное время выключеннного состояния"),

    STOP_TIME_G_ERROR_1_CHANEL_0("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 0"),
    STOP_TIME_G_ERROR_1_CHANEL_1("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 1"),
    STOP_TIME_G_ERROR_1_CHANEL_2("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 2"),
    STOP_TIME_G_ERROR_1_CHANEL_3("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 3"),
    STOP_TIME_G_ERROR_1_CHANEL_4("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 4"),
    STOP_TIME_G_ERROR_1_CHANEL_5("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 5"),
    STOP_TIME_G_ERROR_1_CHANEL_6("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 6"),
    STOP_TIME_G_ERROR_1_CHANEL_7("Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 7"),

    STOP_TIME_G_ERROR_2_CHANEL_0("Время останова измерений G (превышение уставки Gmin) канал 0"),
    STOP_TIME_G_ERROR_2_CHANEL_1("Время останова измерений G (превышение уставки Gmin) канал 1"),
    STOP_TIME_G_ERROR_2_CHANEL_2("Время останова измерений G (превышение уставки Gmin) канал 2"),
    STOP_TIME_G_ERROR_2_CHANEL_3("Время останова измерений G (превышение уставки Gmin) канал 3"),
    STOP_TIME_G_ERROR_2_CHANEL_4("Время останова измерений G (превышение уставки Gmin) канал 4"),
    STOP_TIME_G_ERROR_2_CHANEL_5("Время останова измерений G (превышение уставки Gmin) канал 5"),
    STOP_TIME_G_ERROR_2_CHANEL_6("Время останова измерений G (превышение уставки Gmin) канал 6"),
    STOP_TIME_G_ERROR_2_CHANEL_7("Время останова измерений G (превышение уставки Gmin) канал 7"),

    STOP_TIME_G_ERROR_3_CHANEL_0("Время останова измерений G (превышение уставки Gmax) канал 0"),
    STOP_TIME_G_ERROR_3_CHANEL_1("Время останова измерений G (превышение уставки Gmax) канал 1"),
    STOP_TIME_G_ERROR_3_CHANEL_2("Время останова измерений G (превышение уставки Gmax) канал 2"),
    STOP_TIME_G_ERROR_3_CHANEL_3("Время останова измерений G (превышение уставки Gmax) канал 3"),
    STOP_TIME_G_ERROR_3_CHANEL_4("Время останова измерений G (превышение уставки Gmax) канал 4"),
    STOP_TIME_G_ERROR_3_CHANEL_5("Время останова измерений G (превышение уставки Gmax) канал 5"),
    STOP_TIME_G_ERROR_3_CHANEL_6("Время останова измерений G (превышение уставки Gmax) канал 6"),
    STOP_TIME_G_ERROR_3_CHANEL_7("Время останова измерений G (превышение уставки Gmax) канал 7"),

    WORKING_TIME_G_0("Время наработки G канал 0"),
    WORKING_TIME_G_1("Время наработки G канал 1"),
    WORKING_TIME_G_2("Время наработки G канал 2"),
    WORKING_TIME_G_3("Время наработки G канал 3"),
    WORKING_TIME_G_4("Время наработки G канал 4"),
    WORKING_TIME_G_5("Время наработки G канал 5"),
    WORKING_TIME_G_6("Время наработки G канал 6"),
    WORKING_TIME_G_7("Время наработки G канал 7"),

    STOP_TIME_T_0("Время останова T канал 0"),
    STOP_TIME_T_1("Время останова T канал 1"),
    STOP_TIME_T_2("Время останова T канал 2"),
    STOP_TIME_T_3("Время останова T канал 3"),
    STOP_TIME_T_4("Время останова T канал 4"),
    STOP_TIME_T_5("Время останова T канал 5"),
    STOP_TIME_T_6("Время останова T канал 6"),
    STOP_TIME_T_7("Время останова T канал 7"),

    WORKING_TIME_T_0("Время наработки T канал 0"),
    WORKING_TIME_T_1("Время наработки T канал 1"),
    WORKING_TIME_T_2("Время наработки T канал 2"),
    WORKING_TIME_T_3("Время наработки T канал 3"),
    WORKING_TIME_T_4("Время наработки T канал 4"),
    WORKING_TIME_T_5("Время наработки T канал 5"),
    WORKING_TIME_T_6("Время наработки T канал 6"),
    WORKING_TIME_T_7("Время наработки T канал 7"),

    STOP_TIME_P_0("Время останова P канал 0"),
    STOP_TIME_P_1("Время останова P канал 1"),
    STOP_TIME_P_2("Время останова P канал 2"),
    STOP_TIME_P_3("Время останова P канал 3"),
    STOP_TIME_P_4("Время останова P канал 4"),
    STOP_TIME_P_5("Время останова P канал 5"),
    STOP_TIME_P_6("Время останова P канал 6"),
    STOP_TIME_P_7("Время останова P канал 7"),

    WORKING_TIME_P_0("Время наработки P канал 0"),
    WORKING_TIME_P_1("Время наработки P канал 1"),
    WORKING_TIME_P_2("Время наработки P канал 2"),
    WORKING_TIME_P_3("Время наработки P канал 3"),
    WORKING_TIME_P_4("Время наработки P канал 4"),
    WORKING_TIME_P_5("Время наработки P канал 5"),
    WORKING_TIME_P_6("Время наработки P канал 6"),
    WORKING_TIME_P_7("Время наработки P канал 7"),

    STOP_TIME_MQ_0("Время останова M/Q (при недостоверности G, T, P) канал 0"),
    STOP_TIME_MQ_1("Время останова M/Q (при недостоверности G, T, P) канал 1"),
    STOP_TIME_MQ_2("Время останова M/Q (при недостоверности G, T, P) канал 2"),
    STOP_TIME_MQ_3("Время останова M/Q (при недостоверности G, T, P) канал 3"),
    STOP_TIME_MQ_4("Время останова M/Q (при недостоверности G, T, P) канал 4"),
    STOP_TIME_MQ_5("Время останова M/Q (при недостоверности G, T, P) канал 5"),
    STOP_TIME_MQ_6("Время останова M/Q (при недостоверности G, T, P) канал 6"),
    STOP_TIME_MQ_7("Время останова M/Q (при недостоверности G, T, P) канал 7"),

    WORKING_TIME_MQ_0("Время наработки M/Q канал 0"),
    WORKING_TIME_MQ_1("Время наработки M/Q канал 1"),
    WORKING_TIME_MQ_2("Время наработки M/Q канал 2"),
    WORKING_TIME_MQ_3("Время наработки M/Q канал 3"),
    WORKING_TIME_MQ_4("Время наработки M/Q канал 4"),
    WORKING_TIME_MQ_5("Время наработки M/Q канал 5"),
    WORKING_TIME_MQ_6("Время наработки M/Q канал 6"),
    WORKING_TIME_MQ_7("Время наработки M/Q канал 7"),

    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_0("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 0"),
    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_1("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 1"),
    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_2("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 2"),
    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_3("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 3"),
    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_4("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 4"),
    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_5("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 5"),
    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_6("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 6"),
    ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_7("Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов) канал 7"),

    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_0("Накопленное время останова измерений G (превышение уставки Gmin) канал 0"),
    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_1("Накопленное время останова измерений G (превышение уставки Gmin) канал 1"),
    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_2("Накопленное время останова измерений G (превышение уставки Gmin) канал 2"),
    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_3("Накопленное время останова измерений G (превышение уставки Gmin) канал 3"),
    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_4("Накопленное время останова измерений G (превышение уставки Gmin) канал 4"),
    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_5("Накопленное время останова измерений G (превышение уставки Gmin) канал 5"),
    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_6("Накопленное время останова измерений G (превышение уставки Gmin) канал 6"),
    ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_7("Накопленное время останова измерений G (превышение уставки Gmin) канал 7"),

    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_0("Накопленное время останова измерений G (превышение уставки Gmax) канал 0"),
    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_1("Накопленное время останова измерений G (превышение уставки Gmax) канал 1"),
    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_2("Накопленное время останова измерений G (превышение уставки Gmax) канал 2"),
    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_3("Накопленное время останова измерений G (превышение уставки Gmax) канал 3"),
    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_4("Накопленное время останова измерений G (превышение уставки Gmax) канал 4"),
    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_5("Накопленное время останова измерений G (превышение уставки Gmax) канал 5"),
    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_6("Накопленное время останова измерений G (превышение уставки Gmax) канал 6"),
    ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_7("Накопленное время останова измерений G (превышение уставки Gmax) канал 7"),

    ACCUMULATED_WORKING_TIME_G_0("Накопленное время наработки G канал 0"),
    ACCUMULATED_WORKING_TIME_G_1("Накопленное время наработки G канал 1"),
    ACCUMULATED_WORKING_TIME_G_2("Накопленное время наработки G канал 2"),
    ACCUMULATED_WORKING_TIME_G_3("Накопленное время наработки G канал 3"),
    ACCUMULATED_WORKING_TIME_G_4("Накопленное время наработки G канал 4"),
    ACCUMULATED_WORKING_TIME_G_5("Накопленное время наработки G канал 5"),
    ACCUMULATED_WORKING_TIME_G_6("Накопленное время наработки G канал 6"),
    ACCUMULATED_WORKING_TIME_G_7("Накопленное время наработки G канал 7"),

    ACCUMULATED_STOP_TIME_MQ_0("Накопленное время останова M/Q (при недостоверности G, T, P) канал 0"),
    ACCUMULATED_STOP_TIME_MQ_1("Накопленное время останова M/Q (при недостоверности G, T, P) канал 1"),
    ACCUMULATED_STOP_TIME_MQ_2("Накопленное время останова M/Q (при недостоверности G, T, P) канал 2"),
    ACCUMULATED_STOP_TIME_MQ_3("Накопленное время останова M/Q (при недостоверности G, T, P) канал 3"),
    ACCUMULATED_STOP_TIME_MQ_4("Накопленное время останова M/Q (при недостоверности G, T, P) канал 4"),
    ACCUMULATED_STOP_TIME_MQ_5("Накопленное время останова M/Q (при недостоверности G, T, P) канал 5"),
    ACCUMULATED_STOP_TIME_MQ_6("Накопленное время останова M/Q (при недостоверности G, T, P) канал 6"),
    ACCUMULATED_STOP_TIME_MQ_7("Накопленное время останова M/Q (при недостоверности G, T, P) канал 7"),

    ACCUMULATED_WORKING_TIME_MQ_0("Накопленное время наработки M/Q канал 0"),
    ACCUMULATED_WORKING_TIME_MQ_1("Накопленное время наработки M/Q канал 1"),
    ACCUMULATED_WORKING_TIME_MQ_2("Накопленное время наработки M/Q канал 2"),
    ACCUMULATED_WORKING_TIME_MQ_3("Накопленное время наработки M/Q канал 3"),
    ACCUMULATED_WORKING_TIME_MQ_4("Накопленное время наработки M/Q канал 4"),
    ACCUMULATED_WORKING_TIME_MQ_5("Накопленное время наработки M/Q канал 4"),
    ACCUMULATED_WORKING_TIME_MQ_6("Накопленное время наработки M/Q канал 5"),
    ACCUMULATED_WORKING_TIME_MQ_7("Накопленное время наработки M/Q канал 6"),

    CURRENT_STOP_TIME_ERROR_1_ZONE_0("Время останова измерений при перепаде температур П-О меньше или равно 3 зона 0"),
    CURRENT_STOP_TIME_ERROR_1_ZONE_1("Время останова измерений при перепаде температур П-О меньше или равно 3 зона 1"),
    CURRENT_STOP_TIME_ERROR_1_ZONE_2("Время останова измерений при перепаде температур П-О меньше или равно 3 зона 2"),

    CURRENT_STOP_TIME_ERROR_2_ZONE_0("Время останова измерений с прочими отказами (отказ одного из каналов) зона 0"),
    CURRENT_STOP_TIME_ERROR_2_ZONE_1("Время останова измерений с прочими отказами (отказ одного из каналов) зона 1"),
    CURRENT_STOP_TIME_ERROR_2_ZONE_2("Время останова измерений с прочими отказами (отказ одного из каналов) зона 2"),

    WORKING_TIME_Q_ZONE_0("Время наработки Q зона 0"),
    WORKING_TIME_Q_ZONE_1("Время наработки Q зона 1"),
    WORKING_TIME_Q_ZONE_2("Время наработки Q зона 2"),

    WATER_HEAT_ZONE_0("Количество тепла зона 0"),
    WATER_HEAT_ZONE_1("Количество тепла зона 1"),
    WATER_HEAT_ZONE_2("Количество тепла зона 2"),

    ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_0("Накопленное время останова измерений при перепаде температур П-О меньше или равно 3 зона 0"),
    ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_1("Накопленное время останова измерений при перепаде температур П-О меньше или равно 3 зона 1"),
    ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_2("Накопленное время останова измерений при перепаде температур П-О меньше или равно 3 зона 2"),

    ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_0("Накопленное время останова измерений с прочими отказами (отказ одного из каналов) зона 0"),
    ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_1("Накопленное время останова измерений с прочими отказами (отказ одного из каналов) зона 1"),
    ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_2("Накопленное время останова измерений с прочими отказами (отказ одного из каналов) зона 2"),

    ACCUMULATED_WORKING_TIME_Q_ZONE_0("Накопленное время наработки Q зона 0"),
    ACCUMULATED_WORKING_TIME_Q_ZONE_1("Накопленное время наработки Q зона 1"),
    ACCUMULATED_WORKING_TIME_Q_ZONE_2("Накопленное время наработки Q зона 2"),

    ACCUMULATED_WATER_HEAT_ZONE_0("Накопленное количество тепла зона 0", "470/6/0/float"),
    ACCUMULATED_WATER_HEAT_ZONE_1("Накопленное количество тепла зона 1", "470/6/2/float"),
    ACCUMULATED_WATER_HEAT_ZONE_2("Накопленное количество тепла зона 2", "470/6/4/float");

    private final String property;
    private String register;

    SLAVEConfig(String property) {
        this.property = property;
    }

    SLAVEConfig(String property, String register) {
        this.property = property;
        this.register = register;
    }

    public String getProperty() {
        return property;
    }

    public String getRegister() {
        return register;
    }

    public boolean isInstant() {
        return getRegister() != null;
    }
}
