package net.kencochrane.raven.log4j;

import org.apache.log4j.MDC;
import org.apache.log4j.spi.LoggingEvent;

import net.kencochrane.raven.spi.RavenMDC;

public class Log4jMDC extends RavenMDC {

    private static final ThreadLocal<LoggingEvent> THREAD_LOGGING_EVENT
            = new ThreadLocal<LoggingEvent>();

    public void setThreadLoggingEvent(LoggingEvent event) {
        THREAD_LOGGING_EVENT.set(event);
    }

    public void removeThreadLoggingEvent() {
        THREAD_LOGGING_EVENT.remove();
    }

    @Override
    public Object get(String key) {
        if (THREAD_LOGGING_EVENT.get() != null) {
            return THREAD_LOGGING_EVENT.get().getMDC(key);
        }
        return MDC.get(key);
    }

    @Override
    public void put(String key, Object value) {
        MDC.put(key, value);
    }

    @Override
    public void remove(String key) {
        MDC.remove(key);
    }

}
