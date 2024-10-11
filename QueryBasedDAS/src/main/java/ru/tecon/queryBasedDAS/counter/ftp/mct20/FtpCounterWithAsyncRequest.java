package ru.tecon.queryBasedDAS.counter.ftp.mct20;

import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.serial.*;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.queryBasedDAS.DasException;
import ru.tecon.queryBasedDAS.counter.ftp.FtpClient;
import ru.tecon.queryBasedDAS.counter.CounterAsyncRequest;
import ru.tecon.uploaderService.model.DataModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Maksim Shchelkonogov
 * 06.03.2024
 */
public abstract class FtpCounterWithAsyncRequest extends MctFtpCounter implements CounterAsyncRequest {

    private static final Logger logger = LoggerFactory.getLogger(FtpCounterWithAsyncRequest.class);

    public FtpCounterWithAsyncRequest(FtpCounterInfo info) {
        super(info);
    }

    @Override
    public void loadInstantData(List<DataModel> params, String objectName) throws DasException {

        //
        // ВНИМАНИЕ
        //
        // Работа с мгновенными данными со стороны прибора работает не корректно.
        // Не известно к какому прибору подключаешься если в одном УИН стоит мст20 и мст20-slave
        // т.к. есть только одна точка подключения в файле ip.txt
        // Не может быть в ИУН только мст20-slave (но так есть), т.к. он подключен через мст20 и в этом случае
        // вообще не ясно откуда идут мгновенные данные.
        //

        logger.info("start load instant data from ftpCounter for {}", objectName);

        if (params.isEmpty()) {
            logger.info("finish load instant data from ftpCounter for {} because model is empty", objectName);
            throw new DasException("Пустая модель данных");
        }

        // Получаю ip прибора
        Pattern compile = Pattern.compile(getCounterInfo().getCounterName() + "-(\\d{4})");
        Matcher matcher = compile.matcher(objectName);
        String path;
        if (matcher.matches()) {
            String group = matcher.group(1);
            path = "/" + group.substring(0, 2) + "/" + group + "/ip.txt";
        } else {
            logger.warn("Bad object name {}", objectName);
            throw new DasException("Неправильное имя объекта " + objectName);
        }

        String ip;
        int slaveID = 1;
        try {
            FtpClient ftpClient = new MctFtpClient();
            ftpClient.open();

            try {
                try {
                    InputStream inputStream;

                    try {
                        inputStream = checkFileExistAtFtp(ftpClient.getConnection(), path);
                    } catch (DasException e) {
                        logger.warn("read file {} error {}", path, e.getMessage());
                        ftpClient.close();
                        throw new DasException("Ошибка чтения файла ip.txt");
                    }

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        String line = reader.readLine();
                        if ((line != null) && line.matches("^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$")) {
                            ip = line;
                        } else {
                            throw new DasException("Неожиданные данные в файле ip.txt");
                        }

                        line = reader.readLine();
                        if ((line != null) && line.matches("\\d")) {
                            slaveID = Integer.parseInt(line);
                        }
                    }
                } finally {
                    if (ftpClient.getConnection().isConnected()) {
                        ftpClient.getConnection().completePendingCommand();
                    }
                }
            } catch (IOException ex) {
                logger.warn("error load instant data from ftpCounter for {}", objectName, ex);
                ftpClient.close();
                throw new DasException("Ошибка чтения файла ip.txt");
            }

            ftpClient.close();
        } catch (IOException e) {
            logger.warn("error load instant data from ftpCounter for {}", objectName, e);
            throw new DasException("Ошибка чтения файла ip.txt");
        }

        // Читаю данные из modbus
        try {
            TcpParameters tcpParameter = new TcpParameters();
            tcpParameter.setHost(InetAddress.getByName(ip));
            tcpParameter.setPort(502);
            tcpParameter.setKeepAlive(true);

            SerialParameters serialParameter = new SerialParameters();
            serialParameter.setBaudRate(SerialPort.BaudRate.BAUD_RATE_115200);
            serialParameter.setDataBits(8);
            serialParameter.setParity(SerialPort.Parity.NONE);
            serialParameter.setStopBits(1);

            SerialUtils.setSerialPortFactory(new SerialPortFactoryTcpClient(tcpParameter));
            ModbusMaster master = ModbusMasterFactory.createModbusMasterRTU(serialParameter);
            master.setResponseTimeout(60000);
            master.connect();

            for (DataModel model: params) {
                String propRegister = getPropRegister(model.getParamName().replace(":Текущие данные", ""));
                String[] split = propRegister.split("/");

                int offset = Integer.parseInt(split[0]);
                int quantity = Integer.parseInt(split[1]);

                int[] registerValues = master.readHoldingRegisters(slaveID, offset, quantity);

                int index = Integer.parseInt(split[2]);

                if ("float".equals(split[3])) {
                    ByteBuffer buffer = ByteBuffer.allocate(4)
                            .putShort((short) registerValues[index + 1])
                            .putShort((short) registerValues[index]);

                    String value = String.valueOf(buffer.order(ByteOrder.BIG_ENDIAN).getFloat(0));

                    model.addData(value, LocalDateTime.now());
                }
            }

            master.disconnect();
        } catch (ModbusIOException | SerialPortException | UnknownHostException | ModbusProtocolException |
                 ModbusNumberException e) {
            logger.warn("modbus exception", e);
            throw new DasException(getCounterInfo().getCounterName() + " недоступен");
        }

        params.removeIf(dataModel -> dataModel.getData().isEmpty());

        logger.info("finish load instant data from ftpCounter for {}", objectName);
    }

    protected abstract String getPropRegister(String propName) throws DasException;
}
