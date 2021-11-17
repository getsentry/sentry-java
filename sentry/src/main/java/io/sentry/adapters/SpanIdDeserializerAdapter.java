package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanId;
import java.lang.reflect.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanIdDeserializerAdapter implements JsonDeserializer<SpanId> {

  private final @NotNull SentryOptions options;

  public SpanIdDeserializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable SpanId deserialize(
      final @Nullable JsonElement json,
      final @Nullable Type typeOfT,
      final @NotNull JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : new SpanId(json.getAsString());
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when deserializing SpanId", e);
    }
    return null;
  }
}
