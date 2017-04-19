package io.sentry.unmarshaller.event.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MessageInterface {
    @JsonProperty(value = "message")
    private String message;
    @JsonProperty(value = "params")
    private List<String> params;
    @JsonProperty(value = "formatted")
    private String formatted;
}
