package io.sentry.protocol;

import java.util.List;

// https://docs.sentry.io/development/sdk-dev/event-payloads/message/

public class Message {
  private String formatted;
  private String message;
  private List<String> params;

  public String getFormatted() {
    return formatted;
  }

  public void setFormatted(String formatted) {
    this.formatted = formatted;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<String> getParams() {
    return params;
  }

  public void setParams(List<String> params) {
    this.params = params;
  }
}
