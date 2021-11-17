package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanId;
import java.lang.reflect.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanIdSerializerAdapter implements JsonSerializer<SpanId> {

  private final @NotNull SentryOptions options;

  public SpanIdSerializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable JsonElement serialize(
      final @Nullable SpanId src,
      final @NotNull Type typeOfSrc,
      final @NotNull JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.toString());
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when serializing SpanId", e);
    }
    return null;
  }
}
