package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanStatusDeserializerAdapter implements JsonDeserializer<SpanStatus> {

  private final @NotNull SentryOptions options;

  public SpanStatusDeserializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable SpanStatus deserialize(
      final @Nullable JsonElement json,
      final @NotNull Type typeOfT,
      final @NotNull JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : SpanStatus.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when deserializing SpanStatus", e);
    }
    return null;
  }
}
