package io.sentry.rrweb;

import io.sentry.Breadcrumb;
import io.sentry.ILogger;
import io.sentry.JsonObjectReader;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectWriter;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryLongDate;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.CollectionUtils;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RRWebEvent {

  private @NotNull RRWebEventType type;
  private long timestamp;

  protected RRWebEvent(final @NotNull RRWebEventType type) {
    this.type = type;
    this.timestamp = System.currentTimeMillis();
  }

  protected RRWebEvent() {
   this(RRWebEventType.Custom);
  }

  @NotNull
  public RRWebEventType getType() {
    return type;
  }

  public void setType(final @NotNull RRWebEventType type) {
    this.type = type;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  // region json
  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String TIMESTAMP = "timestamp";
  }

  public static final class Serializer {
    public void serialize(
      @NotNull RRWebEvent baseEvent, @NotNull ObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
      writer.name(JsonKeys.TYPE).value(logger, baseEvent.type);
      writer.name(JsonKeys.TIMESTAMP).value(baseEvent.timestamp);
    }
  }

  public static final class Deserializer {
    @SuppressWarnings("unchecked")
    public boolean deserializeValue(
      @NotNull RRWebEvent baseEvent,
      @NotNull String nextName,
      @NotNull JsonObjectReader reader,
      @NotNull ILogger logger)
      throws Exception {
      switch (nextName) {
        case JsonKeys.TYPE:
          baseEvent.type =
            Objects.requireNonNull(reader.nextOrNull(logger, new RRWebEventType.Deserializer()));
          return true;
        case JsonKeys.TIMESTAMP:
          baseEvent.timestamp = reader.nextLong();
          return true;
      }
      return false;
    }
  }
  // endregion json
}
