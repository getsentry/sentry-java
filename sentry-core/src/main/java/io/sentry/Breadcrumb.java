package io.sentry;

import java.util.Date;
import java.util.Map;

public class Breadcrumb {
  private Date timestamp;
  private String message;
  private String type;
  private Map<String, String> data;
  private String category;
  private SentryLevel level;

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Map<String, String> getData() {
    return data;
  }

  public void setData(Map<String, String> data) {
    this.data = data;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public SentryLevel getLevel() {
    return level;
  }

  public void setLevel(SentryLevel level) {
    this.level = level;
  }
}
