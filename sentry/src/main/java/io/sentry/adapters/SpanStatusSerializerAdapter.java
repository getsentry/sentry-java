package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

@ApiStatus.Internal
public final class SpanStatusSerializerAdapter implements JsonSerializer<SpanStatus> {

  private final @NotNull ILogger logger;

  public SpanStatusSerializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(SpanStatus src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.name().toLowerCase());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing SpanStatus", e);
    }
    return null;
  }
}
