package io.sentry;

import io.sentry.protocol.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryEvent extends SentryBaseEvent implements IUnknownPropertiesConsumer {

  private Message message;

  /** Logger that created the event. */
  private String logger;
  /** Threads that were active when the event occurred. */
  private SentryValues<SentryThread> threads;
  /** One or multiple chained (nested) exceptions. */
  private SentryValues<SentryException> exception;
  /**
   * Severity level of the event. Defaults to `error`.
   *
   * <p>Example:
   *
   * <p>```json {"level": "warning"} ```
   */
  private SentryLevel level;

  /**
   * Manual fingerprint override.
   *
   * <p>A list of strings used to dictate how this event is supposed to be grouped with other events
   * into issues. For more information about overriding grouping see [Customize Grouping with
   * Fingerprints](https://docs.sentry.io/data-management/event-grouping/).
   *
   * <p>```json { "fingerprint": ["myrpc", "POST", "/foo.bar"] }
   */
  private List<String> fingerprint;
  /** List of breadcrumbs recorded before this event. */
  private List<Breadcrumb> breadcrumbs;

  /**
   * Arbitrary extra information set by the user.
   *
   * <p>```json { "extra": { "my_key": 1, "some_other_value": "foo bar" } }```
   */
  private Map<String, Object> extra;

  private Map<String, Object> unknown;
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
  private Map<String, String> modules;
  /** Meta data for event processing and debugging. */
  private DebugMeta debugMeta;

  SentryEvent(SentryId eventId, final Date timestamp) {
    super(eventId);
    setTimestamp(timestamp);
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
  public SentryEvent(final Date timestamp) {
    this(new SentryId(), timestamp);
  }

  public Message getMessage() {
    return message;
  }

  public void setMessage(Message message) {
    this.message = message;
  }

  public String getLogger() {
    return logger;
  }

  public void setLogger(String logger) {
    this.logger = logger;
  }

  public List<SentryThread> getThreads() {
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

  public SentryLevel getLevel() {
    return level;
  }

  public void setLevel(SentryLevel level) {
    this.level = level;
  }

  public List<String> getFingerprints() {
    return fingerprint;
  }

  public void setFingerprints(List<String> fingerprint) {
    this.fingerprint = fingerprint;
  }

  public List<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  public void setBreadcrumbs(List<Breadcrumb> breadcrumbs) {
    this.breadcrumbs = breadcrumbs;
  }

  public void addBreadcrumb(Breadcrumb breadcrumb) {
    if (breadcrumbs == null) {
      breadcrumbs = new ArrayList<>();
    }
    breadcrumbs.add(breadcrumb);
  }

  public void addBreadcrumb(final @Nullable String message) {
    this.addBreadcrumb(new Breadcrumb(message));
  }

  Map<String, Object> getExtras() {
    return extra;
  }

  public void setExtras(Map<String, Object> extra) {
    this.extra = extra;
  }

  public void setExtra(String key, Object value) {
    if (extra == null) {
      extra = new HashMap<>();
    }
    extra.put(key, value);
  }

  public void removeExtra(@NotNull String key) {
    if (extra != null) {
      extra.remove(key);
    }
  }

  public @Nullable Object getExtra(final @NotNull String key) {
    if (extra != null) {
      return extra.get(key);
    }
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @TestOnly
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  Map<String, String> getModules() {
    return modules;
  }

  public void setModules(Map<String, String> modules) {
    this.modules = modules;
  }

  public void setModule(String key, String value) {
    if (modules == null) {
      modules = new HashMap<>();
    }
    modules.put(key, value);
  }

  public void removeModule(@NotNull String key) {
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

  public DebugMeta getDebugMeta() {
    return debugMeta;
  }

  public void setDebugMeta(DebugMeta debugMeta) {
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
