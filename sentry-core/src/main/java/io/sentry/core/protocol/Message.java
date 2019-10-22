package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;

// https://docs.sentry.io/development/sdk-dev/event-payloads/message/

public class Message implements IUnknownPropertiesConsumer {
  private String formatted;
  private String message;
  private List<String> params;
  private Map<String, Object> unknown;

  public String getFormatted() {
    return formatted;
  }

  /** @param formatted */
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

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
