package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single exception.
 *
 * <p>Multiple values inside of an [event](#typedef-Event) represent chained exceptions and should
 * be sorted oldest to newest. For example, consider this Python code snippet:
 *
 * <p>```python try: raise Exception("random boring invariant was not met!") except Exception as e:
 * raise ValueError("something went wrong, help!") from e ```
 *
 * <p>`Exception` would be described first in the values list, followed by a description of
 * `ValueError`:
 *
 * <p>```json { "exception": { "values": [ {"type": "Exception": "value": "random boring invariant
 * was not met!"}, {"type": "ValueError", "value": "something went wrong, help!"}, ] } } ```
 */
public final class SentryException implements JsonUnknown, JsonSerializable {
  /**
   * Exception type, e.g. `ValueError`.
   *
   * <p>At least one of `type` or `value` is required, otherwise the exception is discarded.
   */
  private @Nullable String type;
  /**
   * Human readable display value.
   *
   * <p>At least one of `type` or `value` is required, otherwise the exception is discarded.
   */
  private @Nullable String value;
  /** The optional module, or package which the exception type lives in. */
  private @Nullable String module;
  /** An optional value that refers to a [thread](#typedef-Thread). */
  private @Nullable Long threadId;
  /** Stack trace containing frames of this exception. */
  private @Nullable SentryStackTrace stacktrace;
  /** Mechanism by which this exception was generated and handled. */
  private @Nullable Mechanism mechanism;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  /**
   * The Exception Type.
   *
   * @return the type of the exception.
   */
  public @Nullable String getType() {
    return type;
  }

  /**
   * The Exception Type.
   *
   * @param type type of the exception.
   */
  public void setType(final @Nullable String type) {
    this.type = type;
  }

  /**
   * The exception value.
   *
   * @return the value.
   */
  public @Nullable String getValue() {
    return value;
  }

  /**
   * The exception value
   *
   * @param value The exception message
   */
  public void setValue(final @Nullable String value) {
    this.value = value;
  }

  /**
   * Gets the optional module, or package which the exception type lives in.
   *
   * @return the module.
   */
  public @Nullable String getModule() {
    return module;
  }

  /**
   * Sets the optional module, or package which the exception type lives in.
   *
   * @param module the module.
   */
  public void setModule(final @Nullable String module) {
    this.module = module;
  }

  /**
   * Gets an optional value which refers to a thread in the threads interface.
   *
   * @return the thread id.
   */
  public @Nullable Long getThreadId() {
    return threadId;
  }

  /**
   * Sets an optional value which refers to a thread in the threads interface.
   *
   * @param threadId the thread id.
   */
  public void setThreadId(final @Nullable Long threadId) {
    this.threadId = threadId;
  }

  /**
   * Gets the stack trace.
   *
   * @return the stacktrace.
   */
  public @Nullable SentryStackTrace getStacktrace() {
    return stacktrace;
  }

  /**
   * Sets the stack trace.
   *
   * @param stacktrace the stacktrace of the exception.
   */
  public void setStacktrace(final @Nullable SentryStackTrace stacktrace) {
    this.stacktrace = stacktrace;
  }

  /**
   * Gets an optional mechanism that created this exception.
   *
   * @return the mechanism.
   */
  public @Nullable Mechanism getMechanism() {
    return mechanism;
  }

  /**
   * Sets an optional mechanism that created this exception.
   *
   * @param mechanism the mechanism.
   */
  public void setMechanism(final @Nullable Mechanism mechanism) {
    this.mechanism = mechanism;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String VALUE = "value";
    public static final String MODULE = "module";
    public static final String THREAD_ID = "thread_id";
    public static final String STACKTRACE = "stacktrace";
    public static final String MECHANISM = "mechanism";
  }

  // JsonUnknown

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (type != null) {
      writer.name(JsonKeys.TYPE).value(type);
    }
    if (value != null) {
      writer.name(JsonKeys.VALUE).value(value);
    }
    if (module != null) {
      writer.name(JsonKeys.MODULE).value(module);
    }
    if (threadId != null) {
      writer.name(JsonKeys.THREAD_ID).value(threadId);
    }
    if (stacktrace != null) {
      writer.name(JsonKeys.STACKTRACE).value(logger, stacktrace);
    }
    if (mechanism != null) {
      writer.name(JsonKeys.MECHANISM).value(logger, mechanism);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  // JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<SentryException> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryException deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      SentryException sentryException = new SentryException();
      Map<String, Object> unknown = null;
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TYPE:
            sentryException.type = reader.nextStringOrNull();
            break;
          case JsonKeys.VALUE:
            sentryException.value = reader.nextStringOrNull();
            break;
          case JsonKeys.MODULE:
            sentryException.module = reader.nextStringOrNull();
            break;
          case JsonKeys.THREAD_ID:
            sentryException.threadId = reader.nextLongOrNull();
            break;
          case JsonKeys.STACKTRACE:
            sentryException.stacktrace =
                reader.nextOrNull(logger, new SentryStackTrace.Deserializer());
            break;
          case JsonKeys.MECHANISM:
            sentryException.mechanism = reader.nextOrNull(logger, new Mechanism.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();
      sentryException.setUnknown(unknown);
      return sentryException;
    }
  }
}
