package io.sentry;

import io.sentry.protocol.*;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryEvent extends SentryBaseEvent implements JsonUnknown, JsonSerializable {
  /**
   * Timestamp when the event was created.
   *
   * <p>Indicates when the event was created in the Sentry SDK. The format is either a string as
   * defined in [RFC 3339](https://tools.ietf.org/html/rfc3339) or a numeric (integer or float)
   * value representing the number of seconds that have elapsed since the [Unix
   * epoch](https://en.wikipedia.org/wiki/Unix_time).
   *
   * <p>Sub-microsecond precision is not preserved with numeric values due to precision limitations
   * with floats (at least in our systems). With that caveat in mind, just send whatever is easiest
   * to produce.
   *
   * <p>All timestamps in the event protocol are formatted this way.
   *
   * <p>```json { "timestamp": "2011-05-02T17:41:36Z" } { "timestamp": 1304358096.0 } ```
   */
  private @NotNull Date timestamp;

  private @Nullable Message message;

  /** Logger that created the event. */
  private @Nullable String logger;

  /** Threads that were active when the event occurred. */
  private @Nullable SentryValues<SentryThread> threads;

  /** One or multiple chained (nested) exceptions. */
  private @Nullable SentryValues<SentryException> exception;

  /**
   * Severity level of the event. Defaults to `error`.
   *
   * <p>Example:
   *
   * <p>```json {"level": "warning"} ```
   */
  private @Nullable SentryLevel level;

  /**
   * Transaction name of the event.
   *
   * <p>For example, in a web app, this might be the route name (`"/users/<username>/"` or
   * `UserView`), in a task queue it might be the function + module name.
   */
  private @Nullable String transaction;

  /**
   * Manual fingerprint override.
   *
   * <p>A list of strings used to dictate how this event is supposed to be grouped with other events
   * into issues. For more information about overriding grouping see [Customize Grouping with
   * Fingerprints](https://docs.sentry.io/data-management/event-grouping/).
   *
   * <p>```json { "fingerprint": ["myrpc", "POST", "/foo.bar"] }
   */
  private @Nullable List<String> fingerprint;

  private @Nullable Map<String, Object> unknown;

  /**
   * Name and versions of all installed modules/packages/dependencies in the current
   * environment/application.
   *
   * <p>```json { "django": "3.0.0", "celery": "4.2.1" } ```
   *
   * <p>In Python this is a list of installed packages as reported by `pkg_resources` together with
   * their reported version string.
   *
   * <p>This is primarily used for suggesting to enable certain SDK integrations from within the UI
   * and for making informed decisions on which frameworks to support in future development efforts.
   */
  private @Nullable Map<String, String> modules;

  SentryEvent(final @NotNull SentryId eventId, final @NotNull Date timestamp) {
    super(eventId);
    this.timestamp = timestamp;
  }

  /**
   * SentryEvent ctor with the captured Throwable
   *
   * @param throwable the Throwable or null
   */
  public SentryEvent(final @Nullable Throwable throwable) {
    this();
    this.throwable = throwable;
  }

  public SentryEvent() {
    this(new SentryId(), DateUtils.getCurrentDateTime());
  }

  @TestOnly
  public SentryEvent(final @NotNull Date timestamp) {
    this(new SentryId(), timestamp);
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public Date getTimestamp() {
    return (Date) timestamp.clone();
  }

  public void setTimestamp(final @NotNull Date timestamp) {
    this.timestamp = timestamp;
  }

  public @Nullable Message getMessage() {
    return message;
  }

  public void setMessage(final @Nullable Message message) {
    this.message = message;
  }

  public @Nullable String getLogger() {
    return logger;
  }

  public void setLogger(final @Nullable String logger) {
    this.logger = logger;
  }

  public @Nullable List<SentryThread> getThreads() {
    if (threads != null) {
      return threads.getValues();
    } else {
      return null;
    }
  }

  public void setThreads(final @Nullable List<SentryThread> threads) {
    this.threads = new SentryValues<>(threads);
  }

  public @Nullable List<SentryException> getExceptions() {
    return exception == null ? null : exception.getValues();
  }

  public void setExceptions(final @Nullable List<SentryException> exception) {
    this.exception = new SentryValues<>(exception);
  }

  public @Nullable SentryLevel getLevel() {
    return level;
  }

  public void setLevel(final @Nullable SentryLevel level) {
    this.level = level;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public void setTransaction(final @Nullable String transaction) {
    this.transaction = transaction;
  }

  public @Nullable List<String> getFingerprints() {
    return fingerprint;
  }

  public void setFingerprints(final @Nullable List<String> fingerprint) {
    this.fingerprint = fingerprint != null ? new ArrayList<>(fingerprint) : null;
  }

  @Nullable
  Map<String, String> getModules() {
    return modules;
  }

  public void setModules(final @Nullable Map<String, String> modules) {
    this.modules = CollectionUtils.newHashMap(modules);
  }

  public void setModule(final @NotNull String key, final @NotNull String value) {
    if (modules == null) {
      modules = new HashMap<>();
    }
    modules.put(key, value);
  }

  public void removeModule(final @NotNull String key) {
    if (modules != null) {
      modules.remove(key);
    }
  }

  public @Nullable String getModule(final @NotNull String key) {
    if (modules != null) {
      return modules.get(key);
    }
    return null;
  }

  /**
   * Returns true if any exception was unhandled by the user.
   *
   * @return true if its crashed or false otherwise
   */
  public boolean isCrashed() {
    return getUnhandledException() != null;
  }

  public @Nullable SentryException getUnhandledException() {
    if (exception != null) {
      for (SentryException e : exception.getValues()) {
        if (e.getMechanism() != null
            && e.getMechanism().isHandled() != null
            && !e.getMechanism().isHandled()) {
          return e;
        }
      }
    }
    return null;
  }

  /**
   * Returns true if this event has any sort of exception
   *
   * @return true if errored or false otherwise
   */
  public boolean isErrored() {
    return exception != null && !exception.getValues().isEmpty();
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String MESSAGE = "message";
    public static final String LOGGER = "logger";
    public static final String THREADS = "threads";
    public static final String EXCEPTION = "exception";
    public static final String LEVEL = "level";
    public static final String TRANSACTION = "transaction";
    public static final String FINGERPRINT = "fingerprint";
    public static final String MODULES = "modules";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TIMESTAMP).value(logger, timestamp);
    if (message != null) {
      writer.name(JsonKeys.MESSAGE).value(logger, message);
    }
    if (this.logger != null) {
      writer.name(JsonKeys.LOGGER).value(this.logger);
    }
    if (threads != null && !threads.getValues().isEmpty()) {
      writer.name(JsonKeys.THREADS);
      writer.beginObject();
      writer.name(SentryValues.JsonKeys.VALUES).value(logger, threads.getValues());
      writer.endObject();
    }
    if (exception != null && !exception.getValues().isEmpty()) {
      writer.name(JsonKeys.EXCEPTION);
      writer.beginObject();
      writer.name(SentryValues.JsonKeys.VALUES).value(logger, exception.getValues());
      writer.endObject();
    }
    if (level != null) {
      writer.name(JsonKeys.LEVEL).value(logger, level);
    }
    if (transaction != null) {
      writer.name(JsonKeys.TRANSACTION).value(transaction);
    }
    if (fingerprint != null) {
      writer.name(JsonKeys.FINGERPRINT).value(logger, fingerprint);
    }
    if (modules != null) {
      writer.name(JsonKeys.MODULES).value(logger, modules);
    }
    new SentryBaseEvent.Serializer().serialize(this, writer, logger);
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<SentryEvent> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryEvent deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      SentryEvent event = new SentryEvent();
      Map<String, Object> unknown = null;

      SentryBaseEvent.Deserializer baseEventDeserializer = new SentryBaseEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TIMESTAMP:
            Date deserializedTimestamp = reader.nextDateOrNull(logger);
            if (deserializedTimestamp != null) {
              event.timestamp = deserializedTimestamp;
            }
            break;
          case JsonKeys.MESSAGE:
            event.message = reader.nextOrNull(logger, new Message.Deserializer());
            break;
          case JsonKeys.LOGGER:
            event.logger = reader.nextStringOrNull();
            break;
          case JsonKeys.THREADS:
            reader.beginObject();
            reader.nextName(); // SentryValues.JsonKeys.VALUES
            event.threads =
                new SentryValues<>(reader.nextListOrNull(logger, new SentryThread.Deserializer()));
            reader.endObject();
            break;
          case JsonKeys.EXCEPTION:
            reader.beginObject();
            reader.nextName(); // SentryValues.JsonKeys.VALUES
            event.exception =
                new SentryValues<>(
                    reader.nextListOrNull(logger, new SentryException.Deserializer()));
            reader.endObject();
            break;
          case JsonKeys.LEVEL:
            event.level = reader.nextOrNull(logger, new SentryLevel.Deserializer());
            break;
          case JsonKeys.TRANSACTION:
            event.transaction = reader.nextStringOrNull();
            break;
          case JsonKeys.FINGERPRINT:
            List<String> deserializedFingerprint = (List<String>) reader.nextObjectOrNull();
            if (deserializedFingerprint != null) {
              event.fingerprint = deserializedFingerprint;
            }
            break;
          case JsonKeys.MODULES:
            Map<String, String> deserializedModules =
                (Map<String, String>) reader.nextObjectOrNull();
            event.modules = CollectionUtils.newConcurrentHashMap(deserializedModules);
            break;
          default:
            if (!baseEventDeserializer.deserializeValue(event, nextName, reader, logger)) {
              if (unknown == null) {
                unknown = new ConcurrentHashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
            }
            break;
        }
      }
      event.setUnknown(unknown);
      reader.endObject();
      return event;
    }
  }
}
