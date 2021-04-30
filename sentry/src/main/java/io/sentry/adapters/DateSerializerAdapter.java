package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.DateUtils;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.lang.reflect.Type;
import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DateSerializerAdapter implements JsonSerializer<Date> {

  private final @NotNull SentryOptions options;

  public DateSerializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable JsonElement serialize(
      final @Nullable Date src,
      final @NotNull Type typeOfSrc,
      final @NotNull JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(DateUtils.getTimestamp(src));
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when serializing Date", e);
    }
    return null;
  }
}
