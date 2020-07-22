package io.sentry.core.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryLevelSerializerAdapter implements JsonSerializer<SentryLevel> {

  private final @NotNull ILogger logger;

  public SentryLevelSerializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(SentryLevel src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.name().toLowerCase(Locale.ROOT));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing SentryLevel", e);
    }
    return null;
  }
}
