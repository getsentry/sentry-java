package io.sentry.core;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class Breadcrumb implements Cloneable, IUnknownPropertiesConsumer {

  private final Date timestamp;
  private String message;
  private String type;
  private Map<String, String> data = new ConcurrentHashMap<>();
  private String category;
  private SentryLevel level;
  private Map<String, Object> unknown;

  Breadcrumb(final Date timestamp) {
    this.timestamp = timestamp;
  }

  public Breadcrumb() {
    this(DateUtils.getCurrentDateTime());
  }

  public Breadcrumb(String message) {
    this();
    this.message = message;
  }

  public Date getTimestamp() {
    return (Date) timestamp.clone();
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

  Map<String, String> getData() {
    return data;
  }

  public void setData(@NotNull String key, @NotNull String value) {
    data.put(key, value);
  }

  public void removeData(@NotNull String key) {
    data.remove(key);
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

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @TestOnly
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public Breadcrumb clone() throws CloneNotSupportedException {
    final Breadcrumb clone = (Breadcrumb) super.clone();

    final Map<String, String> dataRef = data;
    if (dataRef != null) {
      final Map<String, String> dataClone = new ConcurrentHashMap<>();

      for (Map.Entry<String, String> item : dataRef.entrySet()) {
        if (item != null) {
          dataClone.put(item.getKey(), item.getValue());
        }
      }

      clone.data = dataClone;
    } else {
      clone.data = null;
    }

    final Map<String, Object> unknownRef = unknown;
    if (unknownRef != null) {
      final Map<String, Object> unknownClone = new HashMap<>();

      for (Map.Entry<String, Object> item : unknownRef.entrySet()) {
        if (item != null) {
          unknownClone.put(item.getKey(), item.getValue()); // shallow copy
        }
      }

      clone.unknown = unknownClone;
    } else {
      clone.unknown = null;
    }

    final SentryLevel levelRef = level;
    clone.level =
        levelRef != null ? SentryLevel.valueOf(levelRef.name().toUpperCase(Locale.ROOT)) : null;

    return clone;
  }
}
