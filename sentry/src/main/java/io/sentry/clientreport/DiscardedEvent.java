package io.sentry.clientreport;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DiscardedEvent implements JsonUnknown, JsonSerializable {

  private final @NotNull String reason;
  private final @NotNull String category;
  private final @NotNull Long quantity;
  private @Nullable Map<String, Object> unknown;

  public DiscardedEvent(@NotNull String reason, @NotNull String category, @NotNull Long quantity) {
    this.reason = reason;
    this.category = category;
    this.quantity = quantity;
  }

  public @NotNull String getReason() {
    return reason;
  }

  public @NotNull String getCategory() {
    return category;
  }

  public @NotNull Long getQuantity() {
    return quantity;
  }

  @Override
  public String toString() {
    return "DiscardedEvent{"
        + "reason='"
        + reason
        + '\''
        + ", category='"
        + category
        + '\''
        + ", quantity="
        + quantity
        + '}';
  }

  public static final class JsonKeys {
    public static final String REASON = "reason";
    public static final String CATEGORY = "category";
    public static final String QUANTITY = "quantity";
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

    writer.name(JsonKeys.REASON).value(reason);
    writer.name(JsonKeys.CATEGORY).value(category);
    writer.name(JsonKeys.QUANTITY).value(quantity);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }

    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<DiscardedEvent> {
    @Override
    public @NotNull DiscardedEvent deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      String reason = null;
      String category = null;
      Long quanity = null;
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.REASON:
            reason = reader.nextStringOrNull();
            break;
          case JsonKeys.CATEGORY:
            category = reader.nextStringOrNull();
            break;
          case JsonKeys.QUANTITY:
            quanity = reader.nextLongOrNull();
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

      if (reason == null) {
        throw missingRequiredFieldException(JsonKeys.REASON, logger);
      }
      if (category == null) {
        throw missingRequiredFieldException(JsonKeys.CATEGORY, logger);
      }
      if (quanity == null) {
        throw missingRequiredFieldException(JsonKeys.QUANTITY, logger);
      }

      DiscardedEvent discardedEvent = new DiscardedEvent(reason, category, quanity);
      discardedEvent.setUnknown(unknown);
      return discardedEvent;
    }

    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }
}
