package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanStatusSerializerAdapter implements JsonSerializer<SpanStatus> {

  private final @NotNull SentryOptions options;

  public SpanStatusSerializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable JsonElement serialize(
      final @Nullable SpanStatus src,
      final @NotNull Type typeOfSrc,
      final @NotNull JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.name().toLowerCase(Locale.ROOT));
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when serializing SpanStatus", e);
    }
    return null;
  }
}
