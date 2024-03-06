package ru.tecon.queryBasedDAS.ejb;

import ru.tecon.uploaderService.ejb.UploaderServiceRemote;
import ru.tecon.uploaderService.ejb.das.ListenerServiceRemote;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

/**
 * @author Maksim Shchelkonogov
 * 05.03.2024
 */
@Stateless
@LocalBean
public class RemoteEJBFactory {

    @EJB
    private QueryBasedDASSingletonBean bean;

    /**
     * Получение удаленного класса загрузки данных
     *
     * @param serverName имя удаленного сервера загрузки данных
     * @return экземпляр класса загрузки данных
     * @throws NamingException в случае ошибки получения удаленного класса
     */
    public UploaderServiceRemote getUploadServiceRemote(String serverName) throws NamingException {
        return getRemoteService(serverName, "uploaderEJBName", UploaderServiceRemote.class);
    }

    /**
     * Получение удаленного класса регистрации слушателей
     *
     * @param serverName имя удаленного сервера загрузки данных
     * @return экземпляр класса регистрации слушателей
     * @throws NamingException в случае ошибки получения удаленного класса
     */
    public ListenerServiceRemote getListenerServiceRemote(String serverName) throws NamingException {
        return getRemoteService(serverName, "listenerServiceEJBName", ListenerServiceRemote.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T getRemoteService(String serverName, String ejbPropName, Class<T> clazz) throws NamingException {
        String[] uploadServerNames = bean.getProperty("uploadServerNames").split(" ");

        for (int i = 0; i < uploadServerNames.length; i++) {
            if (uploadServerNames[i].equals(serverName)) {
                String serverUri = bean.getProperty("uploadServerURIs").split(" ")[i];
                String serverPort = bean.getProperty("uploadServerPorts").split(" ")[i];
                String serviceName = bean.getProperty("uploaderServiceName").split(" ")[i];
                String ejbName = bean.getProperty(ejbPropName).split(" ")[i];

                Context context = new InitialContext(getRemoteServiceProperties(serverUri, serverPort));
                return (T) context.lookup("java:global/" + serviceName + "/" + ejbName + "!" + clazz.getName());
            }
        }

        throw new NamingException("Unknown server name " + serverName);
    }

    /**
     * Получение properties для подключения к CORBA
     *
     * @param serverUri host
     * @param serverPort port
     * @return свойства для подключения
     */
    public Properties getRemoteServiceProperties(String serverUri, String serverPort) {
        Properties jndiProperties = new Properties();

        // TODO провести тестирование на production соединение по ssl port

        // remote ejb over http
        // jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, RemoteEJBContextFactory.FACTORY_CLASS);
        // jndiProperties.put(Context.PROVIDER_URL, "http://" + serverUri + ":" + serverPort + "/ejb-invoker");

        // remote ejb RMI-IIOP/CSIv2
        jndiProperties.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.enterprise.naming.SerialInitContextFactory");
        jndiProperties.setProperty(Context.URL_PKG_PREFIXES, "com.sun.enterprise.naming");
        jndiProperties.setProperty(Context.STATE_FACTORIES, "com.sun.corba.ee.impl.presentation.rmi.JNDIStateFactoryImpl");
        jndiProperties.setProperty("org.omg.CORBA.ORBInitialHost", serverUri);
        jndiProperties.setProperty("org.omg.CORBA.ORBInitialPort", serverPort);

        return jndiProperties;
    }
}
