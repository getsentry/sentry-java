package io.sentry;

import io.sentry.vendor.gson.stream.JsonReader;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonObjectReader implements ObjectReader {

  private final @NotNull JsonReader jsonReader;
  private final @NotNull Deque<RecoveryState> recoveryStates = new ArrayDeque<>();
  private int depth = 0;

  public JsonObjectReader(Reader in) {
    this.jsonReader = new JsonReader(in);
  }

  @Override
  public @Nullable String nextStringOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextString();
  }

  @Override
  public @Nullable Double nextDoubleOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextDouble();
  }

  @Override
  public @Nullable Float nextFloatOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextFloat();
  }

  @Override
  public float nextFloat() throws IOException {
    final double value = jsonReader.nextDouble();
    markValueConsumed();
    return (float) value;
  }

  @Override
  public @Nullable Long nextLongOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextLong();
  }

  @Override
  public @Nullable Integer nextIntegerOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextInt();
  }

  @Override
  public @Nullable Boolean nextBooleanOrNull() throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return nextBoolean();
  }

  @Override
  public void nextUnknown(ILogger logger, Map<String, Object> unknown, String name) {
    RecoveryState recoveryState = null;
    try {
      recoveryState = beginRecovery(peek());
      unknown.put(name, nextObjectOrNull());
    } catch (Exception exception) {
      logger.log(SentryLevel.ERROR, exception, "Error deserializing unknown key: %s", name);
      if (recoveryState != null) {
        try {
          recoverValue(recoveryState);
        } catch (Exception recoveryException) {
          logger.log(
              SentryLevel.ERROR,
              "Stream unrecoverable after unknown key deserialization failure.",
              recoveryException);
        }
      }
    } finally {
      endRecovery(recoveryState);
    }
  }

  @Override
  public <T> @Nullable List<T> nextListOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    beginArray();
    List<T> list = new ArrayList<>();
    if (jsonReader.hasNext()) {
      do {
        final RecoveryState recoveryState = beginRecovery(peek());
        try {
          list.add(deserializer.deserialize(this, logger));
        } catch (Exception e) {
          if (!recoverAfterValueFailure(
              logger,
              e,
              "Failed to deserialize object in list.",
              "Stream unrecoverable, aborting list deserialization.",
              recoveryState)) {
            break;
          }
        } finally {
          endRecovery(recoveryState);
        }
      } while (jsonReader.peek() == JsonToken.BEGIN_OBJECT);
    }
    endArray();
    return list;
  }

  @Override
  public <T> @Nullable Map<String, T> nextMapOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    beginObject();
    Map<String, T> map = new HashMap<>();
    if (jsonReader.hasNext()) {
      do {
        final String key = jsonReader.nextName();
        final RecoveryState recoveryState = beginRecovery(peek());
        try {
          map.put(key, deserializer.deserialize(this, logger));
        } catch (Exception e) {
          if (!recoverAfterValueFailure(
              logger,
              e,
              "Failed to deserialize object in map.",
              "Stream unrecoverable, aborting map deserialization.",
              recoveryState)) {
            break;
          }
        } finally {
          endRecovery(recoveryState);
        }
      } while (jsonReader.peek() == JsonToken.BEGIN_OBJECT || jsonReader.peek() == JsonToken.NAME);
    }

    endObject();
    return map;
  }

  @Override
  public <T> @Nullable Map<String, List<T>> nextMapOfListOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws IOException {

    if (peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    final @NotNull Map<String, List<T>> result = new HashMap<>();

    beginObject();
    if (hasNext()) {
      do {
        final @NotNull String key = nextName();
        final RecoveryState recoveryState = beginRecovery(peek());
        try {
          final @Nullable List<T> list = nextListOrNull(logger, deserializer);
          if (list != null) {
            result.put(key, list);
          }
        } catch (Exception e) {
          if (!recoverAfterValueFailure(
              logger,
              e,
              "Failed to deserialize list in map.",
              "Stream unrecoverable, aborting map-of-lists deserialization.",
              recoveryState)) {
            break;
          }
        } finally {
          endRecovery(recoveryState);
        }
      } while (peek() == JsonToken.BEGIN_OBJECT || peek() == JsonToken.NAME);
    }
    endObject();

    return result;
  }

  @Override
  public <T> @Nullable T nextOrNull(
      @NotNull ILogger logger, @NotNull JsonDeserializer<T> deserializer) throws Exception {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return deserializer.deserialize(this, logger);
  }

  @Override
  public @Nullable Date nextDateOrNull(ILogger logger) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    return ObjectReader.dateOrNull(nextString(), logger);
  }

  @Override
  public @Nullable TimeZone nextTimeZoneOrNull(ILogger logger) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      nextNull();
      return null;
    }
    try {
      return TimeZone.getTimeZone(nextString());
    } catch (Exception e) {
      logger.log(SentryLevel.ERROR, "Error when deserializing TimeZone", e);
    }
    return null;
  }

  /**
   * Decodes JSON into Java primitives/objects (null, boolean, int, long, double, String, Map, List)
   * with full nesting support. To be used at the root level or after calling `nextName()`.
   *
   * @return The deserialized object from json.
   */
  @Override
  public @Nullable Object nextObjectOrNull() throws IOException {
    return new JsonObjectDeserializer().deserialize(this);
  }

  @Override
  public @NotNull JsonToken peek() throws IOException {
    return jsonReader.peek();
  }

  @Override
  public @NotNull String nextName() throws IOException {
    return jsonReader.nextName();
  }

  @Override
  public void beginObject() throws IOException {
    jsonReader.beginObject();
    markValueConsumed();
    depth++;
  }

  @Override
  public void endObject() throws IOException {
    jsonReader.endObject();
    depth--;
  }

  @Override
  public void beginArray() throws IOException {
    jsonReader.beginArray();
    markValueConsumed();
    depth++;
  }

  @Override
  public void endArray() throws IOException {
    jsonReader.endArray();
    depth--;
  }

  @Override
  public boolean hasNext() throws IOException {
    return jsonReader.hasNext();
  }

  @Override
  public int nextInt() throws IOException {
    final int value = jsonReader.nextInt();
    markValueConsumed();
    return value;
  }

  @Override
  public long nextLong() throws IOException {
    final long value = jsonReader.nextLong();
    markValueConsumed();
    return value;
  }

  @Override
  public String nextString() throws IOException {
    final String value = jsonReader.nextString();
    markValueConsumed();
    return value;
  }

  @Override
  public boolean nextBoolean() throws IOException {
    final boolean value = jsonReader.nextBoolean();
    markValueConsumed();
    return value;
  }

  @Override
  public double nextDouble() throws IOException {
    final double value = jsonReader.nextDouble();
    markValueConsumed();
    return value;
  }

  @Override
  public void nextNull() throws IOException {
    jsonReader.nextNull();
    markValueConsumed();
  }

  @Override
  public void setLenient(boolean lenient) {
    jsonReader.setLenient(lenient);
  }

  @Override
  public void skipValue() throws IOException {
    markValueConsumed();
    jsonReader.skipValue();
  }

  private boolean recoverAfterValueFailure(
      final @NotNull ILogger logger,
      final @NotNull Exception error,
      final @NotNull String warningMessage,
      final @NotNull String unrecoverableMessage,
      final @NotNull RecoveryState recoveryState) {
    logger.log(SentryLevel.WARNING, warningMessage, error);
    try {
      recoverValue(recoveryState);
      return true;
    } catch (Exception recoveryException) {
      logger.log(SentryLevel.ERROR, unrecoverableMessage, recoveryException);
      return false;
    }
  }

  private @NotNull RecoveryState beginRecovery(final @NotNull JsonToken startToken) {
    final RecoveryState recoveryState = new RecoveryState(depth, startToken);
    recoveryStates.addLast(recoveryState);
    return recoveryState;
  }

  private void endRecovery(final @Nullable RecoveryState recoveryState) {
    if (recoveryState == null) {
      return;
    }
    if (!recoveryStates.isEmpty() && recoveryStates.peekLast() == recoveryState) {
      recoveryStates.removeLast();
    } else {
      recoveryStates.remove(recoveryState);
    }
  }

  private void markValueConsumed() {
    final @Nullable RecoveryState recoveryState = recoveryStates.peekLast();
    if (recoveryState != null) {
      recoveryState.valueConsumed = true;
    }
  }

  private void recoverValue(final @NotNull RecoveryState recoveryState) throws IOException {
    while (depth > recoveryState.startDepth) {
      final JsonToken token = peek();
      if (token == JsonToken.END_OBJECT) {
        endObject();
      } else if (token == JsonToken.END_ARRAY) {
        endArray();
      } else {
        skipValue();
      }
    }

    if (!recoveryState.valueConsumed && peek() == recoveryState.startToken) {
      skipValue();
    }
  }

  @Override
  public void close() throws IOException {
    jsonReader.close();
  }

  private static final class RecoveryState {
    private final int startDepth;
    private final @NotNull JsonToken startToken;
    private boolean valueConsumed;

    private RecoveryState(final int startDepth, final @NotNull JsonToken startToken) {
      this.startDepth = startDepth;
      this.startToken = startToken;
    }
  }
}
