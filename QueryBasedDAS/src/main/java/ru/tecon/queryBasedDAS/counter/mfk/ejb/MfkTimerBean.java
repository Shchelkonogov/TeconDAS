package ru.tecon.queryBasedDAS.counter.mfk.ejb;

import ru.tecon.queryBasedDAS.counter.mfk.MfkInfo;

import javax.ejb.*;

/**
 * @author Maksim Shchelkonogov
 * 13.10.2024
 */
@Startup
@Singleton
@LocalBean
public class MfkTimerBean {

    @EJB
    private MfkBean bean;

    @Schedule(hour = "*", minute = "*", second = "*/30", persistent = false)
    private void checkLocked(Timer timer) {
        MfkInfo.getInstance().setLocked(bean.getLocked());
    }
}
