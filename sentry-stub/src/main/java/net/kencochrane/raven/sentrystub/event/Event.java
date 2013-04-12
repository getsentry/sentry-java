package net.kencochrane.raven.sentrystub.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
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
//    @JsonProperty(value = "sentry.interfaces.Message")
//    private Map<String, ?> messageInterface;
}
