package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DoubleSerializerAdapter implements JsonSerializer<Double> {
  private final @NotNull SentryOptions options;

  public DoubleSerializerAdapter(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options is required");
  }

  @Override
  public @Nullable JsonElement serialize(
      final @Nullable Double src,
      final @NotNull Type typeOfSrc,
      final @NotNull JsonSerializationContext context) {
    try {
      return src == null
          ? null
          : new JsonPrimitive(BigDecimal.valueOf(src).setScale(6, RoundingMode.DOWN));
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when serializing Double", e);
    }
    return null;
  }
}
