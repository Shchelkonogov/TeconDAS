package ru.tecon.queryBasedDAS.counter.asDTS;

import ru.tecon.queryBasedDAS.counter.CounterInfo;
import ru.tecon.queryBasedDAS.counter.asDTS.ejb.ASDTSMsSqlBean;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.List;

/**
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
public class ASDTSInfo implements CounterInfo {

    private static final String COUNTER_NAME = "IASDTU";

    private final ASDTSMsSqlBean bean;

    public ASDTSInfo() throws NamingException {
        InitialContext ctx = new InitialContext();
        bean = (ASDTSMsSqlBean) ctx.lookup("java:global/queryBasedDAS/ejb/asDTSMsSql");
    }

    @Override
    public String getCounterName() {
        return COUNTER_NAME;
    }

    @Override
    public List<String> getObjects() {
        return bean.getObjects();
    }

    public ASDTSMsSqlBean getBean() {
        return bean;
    }
}
