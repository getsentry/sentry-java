package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.lang.reflect.Type;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TimeZoneDeserializerAdapter implements JsonDeserializer<TimeZone> {

  private final @NotNull SentryOptions options;

  public TimeZoneDeserializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable TimeZone deserialize(
      final @Nullable JsonElement json,
      final @NotNull Type typeOfT,
      final @NotNull JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : TimeZone.getTimeZone(json.getAsString());
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when deserializing TimeZone", e);
    }
    return null;
  }
}
