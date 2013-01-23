package net.kencochrane.raven.logback;

import net.kencochrane.raven.spi.RavenMDC;

import org.slf4j.MDC;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class LogbackMDC extends RavenMDC {

    private static final ThreadLocal<ILoggingEvent> THREAD_LOGGING_EVENT = new ThreadLocal<ILoggingEvent>();

    public void setThreadLoggingEvent(ILoggingEvent event) {
        THREAD_LOGGING_EVENT.set(event);
    }

    public void removeThreadLoggingEvent() {
        THREAD_LOGGING_EVENT.remove();
    }

    @Override
    public Object get(String key) {
        if (THREAD_LOGGING_EVENT.get() != null) {
            return THREAD_LOGGING_EVENT.get().getMDCPropertyMap().get(key);
        }
        return MDC.get(key);
    }

    @Override
    public void put(String key, Object value) {
        MDC.put(key, value.toString());
    }

    @Override
    public void remove(String key) {
        MDC.remove(key);
    }

}
