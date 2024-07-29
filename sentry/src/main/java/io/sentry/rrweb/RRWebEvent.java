package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.Objects;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RRWebEvent)) return false;
    RRWebEvent that = (RRWebEvent) o;
    return timestamp == that.timestamp && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, timestamp);
  }

  // region json
  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String TIMESTAMP = "timestamp";
    public static final String TAG = "tag";
  }

  public static final class Serializer {
    public void serialize(
        final @NotNull RRWebEvent baseEvent,
        final @NotNull ObjectWriter writer,
        final @NotNull ILogger logger)
        throws IOException {
      writer.name(JsonKeys.TYPE).value(logger, baseEvent.type);
      writer.name(JsonKeys.TIMESTAMP).value(baseEvent.timestamp);
    }
  }

  public static final class Deserializer {
    @SuppressWarnings("unchecked")
    public boolean deserializeValue(
        final @NotNull RRWebEvent baseEvent,
        final @NotNull String nextName,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      switch (nextName) {
        case JsonKeys.TYPE:
          baseEvent.type =
              Objects.requireNonNull(
                  reader.nextOrNull(logger, new RRWebEventType.Deserializer()), "");
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
