package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

@ApiStatus.Internal
public final class SpanStatusDeserializerAdapter implements JsonDeserializer<SpanStatus> {

  private final @NotNull ILogger logger;

  public SpanStatusDeserializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public SpanStatus deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : SpanStatus.valueOf(json.getAsString().toUpperCase());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing SpanStatus", e);
    }
    return null;
  }
}
