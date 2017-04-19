package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.ThrowableProxy;
import mockit.Mock;
import mockit.MockUp;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MockUpLoggingEvent extends MockUp<ILoggingEvent> {
    private String loggerName;
    private Marker marker;
    private Level level;
    private String message;
    private Object[] argumentArray;
    private Throwable throwable;
    private Map<String, String> mdcPropertyMap;
    private String threadName;
    private StackTraceElement[] callerData;
    private long timestamp;
    private LoggerContextVO loggerContextVO;


    public MockUpLoggingEvent(String loggerName, Marker marker, Level level, String message,
                              Object[] argumentArray, Throwable t) {
        this(loggerName, marker, level, message, argumentArray, t, null, null, null, System.currentTimeMillis());
    }

    public MockUpLoggingEvent(String loggerName, Marker marker, Level level, String message, Object[] argumentArray,
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

    public MockUpLoggingEvent(String loggerName, Marker marker, Level level, String message, Object[] argumentArray,
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

    @Mock
    public String getThreadName() {
        return threadName;
    }

    @Mock
    public Level getLevel() {
        return level;
    }

    @Mock
    public String getMessage() {
        return message;
    }

    @Mock
    public Object[] getArgumentArray() {
        return argumentArray;
    }

    @Mock
    public String getFormattedMessage() {
        return argumentArray != null ? MessageFormatter.arrayFormat(message, argumentArray).getMessage() : message;
    }

    @Mock
    public String getLoggerName() {
        return loggerName;
    }

    @Mock
    public IThrowableProxy getThrowableProxy() {
        return throwable != null ? new ThrowableProxy(throwable) : null;
    }

    @Mock
    public StackTraceElement[] getCallerData() {
        return callerData != null ? callerData : new StackTraceElement[0];
    }

    @Mock
    public boolean hasCallerData() {
        return callerData != null && callerData.length > 0;
    }

    @Mock
    public Marker getMarker() {
        return marker;
    }

    @Mock
    public Map<String, String> getMDCPropertyMap() {
        return mdcPropertyMap != null ? mdcPropertyMap : Collections.<String, String>emptyMap();
    }

    @Mock
    public long getTimeStamp() {
        return timestamp;
    }

    @Mock
    public LoggerContextVO getLoggerContextVO() {
        return loggerContextVO;
    }
}
