package io.sentry.clientreport;

import static io.sentry.SentryLevel.ERROR;

import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ClientReport implements JsonUnknown, JsonSerializable {

  private final @NotNull Date timestamp;
  private final @NotNull List<DiscardedEvent> discardedEvents;
  private @Nullable Map<String, Object> unknown;

  public ClientReport(@NotNull Date timestamp, @NotNull List<DiscardedEvent> discardedEvents) {
    this.timestamp = timestamp;
    this.discardedEvents = discardedEvents;
  }

  public @NotNull Date getTimestamp() {
    return timestamp;
  }

  public @NotNull List<DiscardedEvent> getDiscardedEvents() {
    return discardedEvents;
  }

  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String DISCARDED_EVENTS = "discarded_events";
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();

    writer.name(JsonKeys.TIMESTAMP).value(DateUtils.getTimestamp(timestamp));
    writer.name(JsonKeys.DISCARDED_EVENTS).value(logger, discardedEvents);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }

    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<ClientReport> {
    @Override
    public @NotNull ClientReport deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      Date timestamp = null;
      List<DiscardedEvent> discardedEvents = new ArrayList<>();
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TIMESTAMP:
            timestamp = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.DISCARDED_EVENTS:
            List<DiscardedEvent> deserializedDiscardedEvents =
                reader.nextListOrNull(logger, new DiscardedEvent.Deserializer());
            discardedEvents.addAll(deserializedDiscardedEvents);
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      if (timestamp == null) {
        throw missingRequiredFieldException(JsonKeys.TIMESTAMP, logger);
      }
      if (discardedEvents.isEmpty()) {
        throw missingRequiredFieldException(JsonKeys.DISCARDED_EVENTS, logger);
      }

      ClientReport clientReport = new ClientReport(timestamp, discardedEvents);
      clientReport.setUnknown(unknown);
      return clientReport;
    }

    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      if (logger.isEnabled(ERROR)) {
        logger.log(SentryLevel.ERROR, message, exception);
      }
      return exception;
    }
  }
}
