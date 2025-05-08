package io.sentry;

import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryLogEvents implements JsonUnknown, JsonSerializable {

  private @NotNull List<SentryLogEvent> items;
  private @Nullable Map<String, Object> unknown;

  public SentryLogEvents(final @NotNull List<SentryLogEvent> items) {
    this.items = items;
  }

  // region json
  public static final class JsonKeys {
    public static final String ITEMS = "items";
  }

  @Override
  @SuppressWarnings("JdkObsolete")
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name("items").value(logger, items);
    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<SentryLogEvents> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryLogEvents deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable Map<String, Object> unknown = null;
      @Nullable List<SentryLogEvent> items = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ITEMS:
            items = reader.nextListOrNull(logger, new SentryLogEvent.Deserializer());
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

      if (items == null) {
        String message = "Missing required field \"" + JsonKeys.ITEMS + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      final SentryLogEvents logEvent = new SentryLogEvents(items);

      logEvent.setUnknown(unknown);

      return logEvent;
    }
  }
  // endregion json
}
