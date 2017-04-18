package io.sentry.unmarshaller.event.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExceptionInterface {
    @JsonProperty(value = "type")
    private String type;
    @JsonProperty(value = "value")
    private String value;
    @JsonProperty(value = "module")
    private String module;
    @JsonProperty(value = "stacktrace")
    private StackTraceInterface stackTraceInterface;
}
