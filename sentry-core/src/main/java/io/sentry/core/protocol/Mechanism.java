package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.Map;

public final class Mechanism implements IUnknownPropertiesConsumer {
  private String type;
  private String description;
  private String helpLink;
  private Boolean handled;
  private Map<String, Object> meta;
  private Map<String, Object> data;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getHelpLink() {
    return helpLink;
  }

  public void setHelpLink(String helpLink) {
    this.helpLink = helpLink;
  }

  public Boolean getHandled() {
    return handled;
  }

  public void setHandled(Boolean handled) {
    this.handled = handled;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(Map<String, Object> data) {
    this.data = data;
  }

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
