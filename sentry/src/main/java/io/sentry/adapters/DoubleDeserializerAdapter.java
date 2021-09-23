package io.sentry.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.DateUtils;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.lang.reflect.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DoubleDeserializerAdapter implements JsonDeserializer<Double> {
  private final @NotNull SentryOptions options;

  public DoubleDeserializerAdapter(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options is required");
  }

  @Override
  public @Nullable Double deserialize(
      final @Nullable JsonElement json,
      final @NotNull Type typeOfT,
      final @NotNull JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return json == null ? null : json.getAsDouble();
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Error when deserializing double, it may be a UTC timestamp represented as string.",
              e);
    }
    // handle legacy format for transactions and spans timestamps
    try {
      return DateUtils.dateToSeconds(DateUtils.getDateTime(json.getAsString()));
    } catch (Exception e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error when deserializing millis timestamp format.", e);
    }
    return null;
  }
}
