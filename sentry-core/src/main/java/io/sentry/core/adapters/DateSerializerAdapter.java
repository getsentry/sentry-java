package io.sentry.core.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.core.DateUtils;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import java.lang.reflect.Type;
import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DateSerializerAdapter implements JsonSerializer<Date> {

  private final @NotNull ILogger logger;

  public DateSerializerAdapter(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  @Override
  public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(DateUtils.getTimestamp(src));
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when serializing Date", e);
    }
    return null;
  }
}
