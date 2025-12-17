package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.sentry.vendor.gson.stream.JsonToken;

public final class SentryMetricsEvents implements JsonUnknown, JsonSerializable {

  private @NotNull List<SentryMetricsEvent> items;
  private @Nullable Map<String, Object> unknown;

  public SentryMetricsEvents(final @NotNull List<SentryMetricsEvent> items) {
    this.items = items;
  }

  public @NotNull List<SentryMetricsEvent> getItems() {
    return items;
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

    writer.name(JsonKeys.ITEMS).value(logger, items);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }

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

  public static final class Deserializer implements JsonDeserializer<SentryMetricsEvents> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryMetricsEvents deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable Map<String, Object> unknown = null;
      @Nullable List<SentryMetricsEvent> items = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ITEMS:
            items = reader.nextListOrNull(logger, new SentryMetricsEvent.Deserializer());
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

      final SentryMetricsEvents metricsEvent = new SentryMetricsEvents(items);

      metricsEvent.setUnknown(unknown);

      return metricsEvent;
    }
  }
  // endregion json
}
