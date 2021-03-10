package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.lang.reflect.Type;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryLevelSerializerAdapter implements JsonSerializer<SentryLevel> {

  private final @NotNull SentryOptions options;

  public SentryLevelSerializerAdapter(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public JsonElement serialize(SentryLevel src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return src == null ? null : new JsonPrimitive(src.name().toLowerCase(Locale.ROOT));
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when serializing SentryLevel", e);
    }
    return null;
  }
}
