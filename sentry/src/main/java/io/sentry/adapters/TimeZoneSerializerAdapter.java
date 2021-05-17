package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.lang.reflect.Type;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TimeZoneSerializerAdapter implements JsonSerializer<TimeZone> {

  private final @NotNull SentryOptions options;

  public TimeZoneSerializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable JsonElement serialize(
      final @Nullable TimeZone src,
      final @NotNull Type typeOfSrc,
      final @NotNull JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.getID());
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when serializing TimeZone", e);
    }
    return null;
  }
}
