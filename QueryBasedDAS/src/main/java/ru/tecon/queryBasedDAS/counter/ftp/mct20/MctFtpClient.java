package ru.tecon.queryBasedDAS.counter.ftp.mct20;

import ru.tecon.queryBasedDAS.counter.ftp.FtpClient;

/**
 * @author Maksim Shchelkonogov
 * 12.03.2024
 */
public class MctFtpClient extends FtpClient {

    // TODO adjust on production mode (moek 10.98.254.10, moek vpn 172.16.4.134)
    private static final String server = "10.98.254.10";
    private static final int port = 21;
    private static final String user = "ftp_device";
    private static final char[] password = {'J', 'g', '4', 'H', 'Q', 'O', '7', 'O'};

    public MctFtpClient() {
        super(server, port, user, password);
    }
}
