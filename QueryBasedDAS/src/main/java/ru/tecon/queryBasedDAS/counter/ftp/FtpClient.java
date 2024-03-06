package ru.tecon.queryBasedDAS.counter.ftp;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Класс для подключения к ftp МСТ20
 *
 * @author Maksim Shchelkonogov
 * 08.02.2024
 */
public class FtpClient {

    // TODO adjust on production mode (moek 10.98.254.10, moek vpn 172.16.4.47)
    private static final String server = "10.98.254.10";
    private static final int port = 21;
    private static final String user = "ftp_device";
    private static final char[] password = {'J', 'g', '4', 'H', 'Q', 'O', '7', 'O'};
    private FTPClient ftp;

    /**
     * Открытие соединения с ftp
     *
     * @throws IOException в случае ошибки открытия соединения
     */
    public void open() throws IOException {
        open(false);
    }

    /**
     * Открытие соединения с ftp
     *
     * @param logging true если выводить логи ftp соединения
     * @throws IOException в случае ошибки открытия соединения
     */
    public void open(boolean logging) throws IOException {
        ftp = new FTPClient();
        if (logging) {
            ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out), true));
        }
        ftp.connect(server, port);
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("Exception in connecting to FTP Server");
        }

        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);

        boolean connect = ftp.login(user, new String(password));

        if (!connect) {
            ftp.disconnect();
            throw new IOException("Exception in connecting to FTP Server");
        }
    }

    /**
     * Закрыть соединение с ftp
     *
     * @throws IOException в случае ошибки закрытия соединения
     */
    public void close() throws IOException {
        ftp.disconnect();
    }

    public FTPClient getConnection() {
        return ftp;
    }
}
