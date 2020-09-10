package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;
import java.lang.reflect.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryIdSerializerAdapter implements JsonSerializer<SentryId> {

  private final @NotNull ILogger logger;

  public SentryIdSerializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(SentryId src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.toString());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing SentryId", e);
    }
    return null;
  }
}
