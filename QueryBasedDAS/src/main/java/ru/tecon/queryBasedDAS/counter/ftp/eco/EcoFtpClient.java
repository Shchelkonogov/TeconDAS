package ru.tecon.queryBasedDAS.counter.ftp.eco;

import ru.tecon.queryBasedDAS.counter.ftp.FtpClient;

/**
 * Класс для подключения к ftp МСТ20
 *
 * @author Maksim Shchelkonogov
 * 08.02.2024
 */
public class EcoFtpClient extends FtpClient {

    private static final String server = "172.16.1.253";
    private static final int port = 21;
    private static final String user = "disp";
    private static final char[] password = {'t', 's', 'a', '4', 'd', 'm', 'i', 'h'};

    public EcoFtpClient() {
        super(server, port, user, password);
    }
}
