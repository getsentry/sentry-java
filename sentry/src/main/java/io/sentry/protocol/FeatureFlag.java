package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FeatureFlag implements JsonUnknown, JsonSerializable {

  public static final @NotNull String DATA_PREFIX = "flag.evaluation.";

  /** Name of the feature flag. */
  private @NotNull String flag;

  /** Evaluation result of the feature flag. */
  private boolean result;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public FeatureFlag(@NotNull String flag, boolean result) {
    this.flag = flag;
    this.result = result;
  }

  public @NotNull String getFlag() {
    return flag;
  }

  public void setFlag(final @NotNull String flag) {
    this.flag = flag;
  }

  @NotNull
  public Boolean getResult() {
    return result;
  }

  public void setResult(final @NotNull Boolean result) {
    this.result = result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final @NotNull FeatureFlag otherFlag = (FeatureFlag) o;
    return Objects.equals(flag, otherFlag.flag) && Objects.equals(result, otherFlag.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(flag, result);
  }

  // region json

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class JsonKeys {
    public static final String FLAG = "flag";
    public static final String RESULT = "result";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();

    writer.name(JsonKeys.FLAG).value(flag);
    writer.name(JsonKeys.RESULT).value(result);
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<FeatureFlag> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull FeatureFlag deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      @Nullable String flag = null;
      @Nullable Boolean result = null;
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.FLAG:
            flag = reader.nextStringOrNull();
            break;
          case JsonKeys.RESULT:
            result = reader.nextBooleanOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      if (flag == null) {
        String message = "Missing required field \"" + JsonKeys.FLAG + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }
      if (result == null) {
        String message = "Missing required field \"" + JsonKeys.RESULT + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }
      FeatureFlag app = new FeatureFlag(flag, result);
      app.setUnknown(unknown);
      reader.endObject();
      return app;
    }
  }
}
