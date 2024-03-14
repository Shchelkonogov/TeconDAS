package ru.tecon.queryBasedDAS.counter.ftp;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Класс для подключения к ftp
 *
 * @author Maksim Shchelkonogov
 * 08.02.2024
 */
public class FtpClient {

    private final String server;
    private final int port;
    private final String user;
    private final char[] password;
    private FTPClient ftp;

    public FtpClient(String server, int port, String user, char[] password) {
        this.server = server;
        this.port = port;
        this.user = user;
        this.password = password;
    }

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
