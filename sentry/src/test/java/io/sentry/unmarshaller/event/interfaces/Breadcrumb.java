package io.sentry.unmarshaller.event.interfaces;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Breadcrumb {
	@JsonProperty("type")
	private String type;
	@JsonProperty("timestamp")
	private String timestamp;
	@JsonProperty("level")
	private String level;
	@JsonProperty("message")
	private String message;
	@JsonProperty("category")
	private String category;
	@JsonProperty("data")
	private Map<String, String> data;
}
