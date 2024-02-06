package ru.tecon.queryBasedDAS;

import ru.tecon.uploaderService.ejb.das.ListenerServiceRemote;
import ru.tecon.uploaderService.ejb.UploaderServiceRemote;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.Properties;

/**
 * Класс для получения доступа к серверу загрузки данных
 *
 * @author Maksim Shchelkonogov
 * 10.01.2024
 */
public class UploadServiceEJBFactory {

    private UploadServiceEJBFactory() {
    }

    /**
     * Проверка корректности ввода свойств
     */
    public static void checkProps() {
        try {
            Properties appProperties = PropertiesLoader.loadProperties("app.properties");

            String[] uploadServerNames = appProperties.getProperty("uploadServerNames").split(" ");
            String[] uploadServerURIs = appProperties.getProperty("uploadServerURIs").split(" ");
            String[] uploadServerPorts = appProperties.getProperty("uploadServerPorts").split(" ");
            String[] uploaderServiceName = appProperties.getProperty("uploaderServiceName").split(" ");
            String[] uploaderEJBName = appProperties.getProperty("uploaderEJBName").split(" ");
            String[] listenerServiceEJBName = appProperties.getProperty("listenerServiceEJBName").split(" ");

            int remoteCount = uploadServerNames.length;

            // проверка записи параметров
            if ((remoteCount != uploadServerURIs.length) ||
                    (remoteCount != uploadServerPorts.length) ||
                    (remoteCount != uploaderServiceName.length) ||
                    (remoteCount != uploaderEJBName.length) ||
                    (remoteCount != listenerServiceEJBName.length)) {
                throw new RuntimeException("Error application parameters");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Получение удаленного класса загрузки данных
     *
     * @param serverName имя удаленного сервера загрузки данных
     * @return экземпляр класса загрузки данных
     * @throws IOException в случае ошибки получения удаленного класса
     * @throws NamingException в случае ошибки получения удаленного класса
     */
    public static UploaderServiceRemote getUploadServiceRemote(String serverName) throws IOException, NamingException {
        Properties appProperties = PropertiesLoader.loadProperties("app.properties");

        String[] uploadServerNames = appProperties.getProperty("uploadServerNames").split(" ");

        for (int i = 0; i < uploadServerNames.length; i++) {
            if (uploadServerNames[i].equals(serverName)) {
                String serverUri = appProperties.getProperty("uploadServerURIs").split(" ")[i];
                String serverPort = appProperties.getProperty("uploadServerPorts").split(" ")[i];
                String serviceName = appProperties.getProperty("uploaderServiceName").split(" ")[i];
                String ejbName = appProperties.getProperty("uploaderEJBName").split(" ")[i];

                Context context = getContext(serverUri, serverPort);
                return (UploaderServiceRemote) context.lookup("java:global/" + serviceName + "/" + ejbName + "!" + UploaderServiceRemote.class.getName());
            }
        }

        throw new NamingException("Unknown server name " + serverName);
    }

    /**
     * Получение удаленного класса регистрации слушателей
     *
     * @param serverName имя удаленного сервера загрузки данных
     * @return экземпляр класса регистрации слушателей
     * @throws IOException в случае ошибки получения удаленного класса
     * @throws NamingException в случае ошибки получения удаленного класса
     */
    public static ListenerServiceRemote getListenerServiceRemote(String serverName) throws IOException, NamingException {
        Properties appProperties = PropertiesLoader.loadProperties("app.properties");

        String[] uploadServerNames = appProperties.getProperty("uploadServerNames").split(" ");

        for (int i = 0; i < uploadServerNames.length; i++) {
            if (uploadServerNames[i].equals(serverName)) {
                String serverUri = appProperties.getProperty("uploadServerURIs").split(" ")[i];
                String serverPort = appProperties.getProperty("uploadServerPorts").split(" ")[i];
                String serviceName = appProperties.getProperty("uploaderServiceName").split(" ")[i];
                String ejbName = appProperties.getProperty("listenerServiceEJBName").split(" ")[i];

                Context context = getContext(serverUri, serverPort);
                return (ListenerServiceRemote) context.lookup("java:global/" + serviceName + "/" + ejbName + "!" + ListenerServiceRemote.class.getName());
            }
        }

        throw new NamingException("Unknown server name " + serverName);
    }

    private static Context getContext(String serverUri, String serverPort) throws NamingException {
        Properties jndiProperties = new Properties();

        // remote ejb over http
//        jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, RemoteEJBContextFactory.FACTORY_CLASS);
//        jndiProperties.put(Context.PROVIDER_URL, "http://" + serverUri + ":" + serverPort + "/ejb-invoker");

        // remote ejb RMI-IIOP/CSIv2
        jndiProperties.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.enterprise.naming.SerialInitContextFactory");
        jndiProperties.setProperty(Context.URL_PKG_PREFIXES, "com.sun.enterprise.naming");
        jndiProperties.setProperty(Context.STATE_FACTORIES, "com.sun.corba.ee.impl.presentation.rmi.JNDIStateFactoryImpl");
        jndiProperties.setProperty("org.omg.CORBA.ORBInitialHost", serverUri);
        jndiProperties.setProperty("org.omg.CORBA.ORBInitialPort", serverPort);
        return new InitialContext(jndiProperties);
    }
}
