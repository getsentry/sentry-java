package io.sentry;

import io.sentry.protocol.*;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryEvent extends SentryBaseEvent implements IUnknownPropertiesConsumer {
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
  private final @NotNull Date timestamp;

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
  /** Meta data for event processing and debugging. */
  private @Nullable DebugMeta debugMeta;

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

  public void setThreads(List<SentryThread> threads) {
    this.threads = new SentryValues<>(threads);
  }

  public List<SentryException> getExceptions() {
    return exception == null ? null : exception.getValues();
  }

  public void setExceptions(List<SentryException> exception) {
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

  public @NotNull List<String> getFingerprints() {
    return fingerprint;
  }

  public void setFingerprints(final @NotNull List<String> fingerprint) {
    this.fingerprint = fingerprint;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @TestOnly
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Nullable
  Map<String, String> getModules() {
    return modules;
  }

  public void setModules(final @Nullable Map<String, String> modules) {
    this.modules = modules;
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

  public @Nullable DebugMeta getDebugMeta() {
    return debugMeta;
  }

  public void setDebugMeta(final @Nullable DebugMeta debugMeta) {
    this.debugMeta = debugMeta;
  }

  /**
   * Returns true if any exception was unhandled by the user.
   *
   * @return true if its crashed or false otherwise
   */
  public boolean isCrashed() {
    if (exception != null) {
      for (SentryException e : exception.getValues()) {
        if (e.getMechanism() != null
            && e.getMechanism().isHandled() != null
            && !e.getMechanism().isHandled()) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns true if this event has any sort of exception
   *
   * @return true if errored or false otherwise
   */
  public boolean isErrored() {
    return exception != null && !exception.getValues().isEmpty();
  }
}
