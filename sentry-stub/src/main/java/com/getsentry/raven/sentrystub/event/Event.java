package com.getsentry.raven.sentrystub.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getsentry.raven.sentrystub.event.interfaces.ExceptionInterface;
import com.getsentry.raven.sentrystub.event.interfaces.MessageInterface;
import com.getsentry.raven.sentrystub.event.interfaces.StackTraceInterface;

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
    @JsonProperty(value = "sdk")
    private Map<String, String> sdk;
    @JsonProperty(value = "culprit")
    private String culprit;
    @JsonProperty(value = "tags")
    private Map<String, String> tags;
    @JsonProperty(value = "server_name")
    private String serverName;
    @JsonProperty(value = "release")
    private String release;
    @JsonProperty(value = "environment")
    private String environment;
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

    @JsonProperty(value = "sentry.interfaces.Stacktrace")
    public void setStackTraceInterfaceLong(StackTraceInterface stackTraceInterface) {
        this.stackTraceInterface = stackTraceInterface;
    }
}
