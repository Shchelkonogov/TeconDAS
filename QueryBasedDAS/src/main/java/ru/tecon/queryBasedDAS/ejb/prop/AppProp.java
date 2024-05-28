package ru.tecon.queryBasedDAS.ejb.prop;

import java.util.*;

/**
 * @author Maksim Shchelkonogov
 * 21.05.2024
 */
public class AppProp {

    private String dasName;
    private List<String> counters;
    private Map<String, Remote> remotes;

    private AppProp() {
    }

    public String getDasName() {
        return dasName;
    }

    public List<String> getCounters() {
        return new ArrayList<>(counters);
    }

    public Map<String, Remote> getRemotes() {
        return new HashMap<>(this.remotes);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AppProp.class.getSimpleName() + "[", "]")
                .add("dasName='" + dasName + "'")
                .add("counters=" + counters)
                .add("remotes=" + remotes)
                .toString();
    }

    public static class Remote {

        private String url;
        private int port;
        private String serviceName;
        private String ejbName;
        private String listenerEjbName;

        private Remote() {
        }

        public String getUrl() {
            return url;
        }

        public int getPort() {
            return port;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getListenerEjbName() {
            return listenerEjbName;
        }

        public String getEjbName() {
            return ejbName;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Remote.class.getSimpleName() + "[", "]")
                    .add("url='" + url + "'")
                    .add("port=" + port)
                    .add("serviceName='" + serviceName + "'")
                    .add("ejbName='" + ejbName + "'")
                    .add("listenerEjbName='" + listenerEjbName + "'")
                    .toString();
        }
    }
}
