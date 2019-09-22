package io.sentry.logback;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

public class TestLoggingEvent implements ILoggingEvent {
    private final String loggerName;
    private final Marker marker;
    private final Level level;
    private final String message;
    private final Object[] argumentArray;
    private final Throwable throwable;
    private final Map<String, String> mdcPropertyMap;
    private final String threadName;
    private final StackTraceElement[] callerData;
    private final long timestamp;
    private final LoggerContextVO loggerContextVO;

        public TestLoggingEvent(String loggerName, Marker marker, Level level, String message,
                              Object[] argumentArray, Throwable t) {
        this(loggerName, marker, level, message, argumentArray, t, null, null, null, System.currentTimeMillis());
    }

    public TestLoggingEvent(String loggerName, Marker marker, Level level, String message, Object[] argumentArray,
                              Throwable throwable, Map<String, String> mdcPropertyMap, String threadName,
                              StackTraceElement[] callerData, long timestamp) {
        this(loggerName,
                marker,
                level,
                message,
                argumentArray,
                throwable,
                mdcPropertyMap,
                threadName,
                callerData,
                timestamp,
                new HashMap<String, String>());
    }

    public TestLoggingEvent(String loggerName, Marker marker, Level level, String message, Object[] argumentArray,
                              Throwable throwable, Map<String, String> mdcPropertyMap, String threadName,
                              StackTraceElement[] callerData, long timestamp, Map<String, String> contextProperties) {
        this.loggerName = loggerName;
        this.marker = marker;
        this.level = level;
        this.message = message;
        this.argumentArray = argumentArray;
        this.throwable = throwable;
        this.mdcPropertyMap = mdcPropertyMap;
        this.threadName = threadName;
        this.callerData = callerData;
        this.timestamp = timestamp;
        this.loggerContextVO = new LoggerContextVO("loggerContextOf" + loggerName, contextProperties, System.currentTimeMillis());
    }

    @Override
    public String getThreadName() {
        return threadName;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Object[] getArgumentArray() {
        return argumentArray;
    }

    @Override
    public String getFormattedMessage() {
        return argumentArray != null ? MessageFormatter.arrayFormat(message, argumentArray).getMessage() : message;
    }

    @Override
    public String getLoggerName() {
        return loggerName;
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
        return loggerContextVO;
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
        return throwable != null ? new ThrowableProxy(throwable) : null;
    }

    @Override
    public StackTraceElement[] getCallerData() {
        return callerData != null ? callerData : new StackTraceElement[0];
    }

    @Override
    public boolean hasCallerData() {
        return callerData != null && callerData.length > 0;
    }

    @Override
    public Marker getMarker() {
        return marker;
    }

    @Override
    public Map<String, String> getMDCPropertyMap() {
        return mdcPropertyMap != null ? mdcPropertyMap : Collections.<String, String>emptyMap();
    }

    @Override
    public Map<String, String> getMdc() {
        return getMDCPropertyMap();
    }

    @Override
    public long getTimeStamp() {
        return timestamp;
    }

    @Override
    public void prepareForDeferredProcessing() {

    }
}
