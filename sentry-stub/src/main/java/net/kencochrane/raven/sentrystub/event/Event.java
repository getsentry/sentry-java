package net.kencochrane.raven.sentrystub.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.kencochrane.raven.sentrystub.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.sentrystub.event.interfaces.MessageInterface;
import net.kencochrane.raven.sentrystub.event.interfaces.StackTraceInterface;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class Event {
    @JsonProperty(value = "event_id", required = true)
    private String eventId;
    @JsonProperty(value = "checksum")
    private String checksum;
    @JsonProperty(value = "message")
    private String message;
    @JsonProperty(value = "timestamp", required = true)
    private Date timestamp;
    @JsonProperty(value = "level")
    private String level;
    @JsonProperty(value = "logger")
    private String logger;
    @JsonProperty(value = "platform")
    private String platform;
    @JsonProperty(value = "culprit")
    private String culprit;
    @JsonProperty(value = "tags")
    private Map<String, String> tags;
    @JsonProperty(value = "server_name")
    private String serverName;
    @JsonProperty(value = "modules")
    private Map<String, String> modules;
    @JsonProperty(value = "extra")
    private Map<String, Object> extras;
    @JsonProperty(value = "sentry.interfaces.Message")
    private MessageInterface messageInterface;
    private List<ExceptionInterface> exceptionInterfaces;
    private StackTraceInterface stackTraceInterface;

    @JsonProperty(value = "exception")
    public void setExceptionInterfaces(List<ExceptionInterface> exceptionInterfaces) {
        this.exceptionInterfaces = exceptionInterfaces;
    }

    @JsonProperty(value = "stacktrace")
    public void setStackTraceInterface(StackTraceInterface stackTraceInterface) {
        this.stackTraceInterface = stackTraceInterface;
    }

    @JsonProperty(value = "sentry.interfaces.Exception")
    public void setExceptionInterfacesLong(List<ExceptionInterface> exceptionInterfaces) {
        this.exceptionInterfaces = exceptionInterfaces;
    }

    @JsonProperty(value = "sentry.interfaces.StackTrace")
    public void setStackTraceInterfaceLong(StackTraceInterface stackTraceInterface) {
        this.stackTraceInterface = stackTraceInterface;
    }
}
