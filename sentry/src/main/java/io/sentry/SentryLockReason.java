package io.sentry;

import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents an instance of a held lock (java monitor object) in a thread. */
public final class SentryLockReason implements JsonUnknown, JsonSerializable {

  public static final int LOCKED = 1;
  public static final int WAITING = 2;
  public static final int SLEEPING = 4;
  public static final int BLOCKED = 8;

  public static final int ANY = LOCKED | WAITING | SLEEPING | BLOCKED;

  private int type;
  private @Nullable String address;
  private @Nullable String packageName;
  private @Nullable String className;
  private @Nullable Long threadId;
  private @Nullable Map<String, Object> unknown;

  public SentryLockReason() {}

  public SentryLockReason(final @NotNull SentryLockReason other) {
    this.type = other.type;
    this.address = other.address;
    this.packageName = other.packageName;
    this.className = other.className;
    this.threadId = other.threadId;
    this.unknown = CollectionUtils.newConcurrentHashMap(other.unknown);
  }

  @SuppressWarnings("unused")
  public int getType() {
    return type;
  }

  public void setType(final int type) {
    this.type = type;
  }

  @Nullable
  public String getAddress() {
    return address;
  }

  public void setAddress(final @Nullable String address) {
    this.address = address;
  }

  @Nullable
  public String getPackageName() {
    return packageName;
  }

  public void setPackageName(final @Nullable String packageName) {
    this.packageName = packageName;
  }

  @Nullable
  public String getClassName() {
    return className;
  }

  public void setClassName(final @Nullable String className) {
    this.className = className;
  }

  @Nullable
  public Long getThreadId() {
    return threadId;
  }

  public void setThreadId(final @Nullable Long threadId) {
    this.threadId = threadId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SentryLockReason that = (SentryLockReason) o;
    return Objects.equals(address, that.address);
  }

  @Override
  public int hashCode() {
    return Objects.hash(address);
  }

  // region json

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String ADDRESS = "address";
    public static final String PACKAGE_NAME = "package_name";
    public static final String CLASS_NAME = "class_name";
    public static final String THREAD_ID = "thread_id";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TYPE).value(type);
    if (address != null) {
      writer.name(JsonKeys.ADDRESS).value(address);
    }
    if (packageName != null) {
      writer.name(JsonKeys.PACKAGE_NAME).value(packageName);
    }
    if (className != null) {
      writer.name(JsonKeys.CLASS_NAME).value(className);
    }
    if (threadId != null) {
      writer.name(JsonKeys.THREAD_ID).value(threadId);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<SentryLockReason> {

    @Override
    public @NotNull SentryLockReason deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      final SentryLockReason sentryLockReason = new SentryLockReason();
      Map<String, Object> unknown = null;
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TYPE:
            sentryLockReason.type = reader.nextInt();
            break;
          case JsonKeys.ADDRESS:
            sentryLockReason.address = reader.nextStringOrNull();
            break;
          case JsonKeys.PACKAGE_NAME:
            sentryLockReason.packageName = reader.nextStringOrNull();
            break;
          case JsonKeys.CLASS_NAME:
            sentryLockReason.className = reader.nextStringOrNull();
            break;
          case JsonKeys.THREAD_ID:
            sentryLockReason.threadId = reader.nextLongOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      sentryLockReason.setUnknown(unknown);
      reader.endObject();
      return sentryLockReason;
    }
  }

  // endregion
}
