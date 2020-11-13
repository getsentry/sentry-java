package io.sentry;

import io.sentry.protocol.*;
import java.util.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryEvent implements IUnknownPropertiesConsumer {
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
  private final Date timestamp;
  /**
   * Unique identifier of this event.
   *
   * <p>Hexadecimal string representing a uuid4 value. The length is exactly 32 characters. Dashes
   * are not allowed. Has to be lowercase.
   *
   * <p>Even though this field is backfilled on the server with a new uuid4, it is strongly
   * recommended to generate that uuid4 clientside. There are some features like user feedback which
   * are easier to implement that way, and debugging in case events get lost in your Sentry
   * installation is also easier.
   *
   * <p>Example:
   *
   * <p>```json { "event_id": "fc6d8c0c43fc4630ad850ee518f1b9d0" } ```
   */
  private SentryId eventId;
  /** The captured Throwable */
  private transient @Nullable Throwable throwable;
  /** Custom parameterized message for this event. */
  private Message message;
  /**
   * Server or device name the event was generated on.
   *
   * <p>This is supposed to be a hostname.
   */
  private String serverName;
  /**
   * Platform identifier of this event (defaults to "other").
   *
   * <p>A string representing the platform the SDK is submitting from. This will be used by the
   * Sentry interface to customize various components in the interface, but also to enter or skip
   * stacktrace processing.
   *
   * <p>Acceptable values are: `as3`, `c`, `cfml`, `cocoa`, `csharp`, `elixir`, `haskell`, `go`,
   * `groovy`, `java`, `javascript`, `native`, `node`, `objc`, `other`, `perl`, `php`, `python`,
   * `ruby`
   */
  private String platform;
  /**
   * The release version of the application.
   *
   * <p>**Release versions must be unique across all projects in your organization.** This value can
   * be the git SHA for the given project, or a product identifier with a semantic version.
   */
  private String release;
  /**
   * Program's distribution identifier.
   *
   * <p>The distribution of the application.
   *
   * <p>Distributions are used to disambiguate build or deployment variants of the same release of
   * an application. For example, the dist can be the build number of an XCode build or the version
   * code of an Android build.
   */
  private String dist;
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
   * Transaction name of the event.
   *
   * <p>For example, in a web app, this might be the route name (`"/users/<username>/"` or
   * `UserView`), in a task queue it might be the function + module name.
   */
  private String transaction;
  /**
   * The environment name, such as `production` or `staging`.
   *
   * <p>```json { "environment": "production" } ```
   */
  private String environment;
  /** Information about the user who triggered this event. */
  private User user;
  /** Information about a web request that occurred during the event. */
  private Request request;
  /** Information about the Sentry SDK that generated this event. */
  private SdkVersion sdk;
  /** Contexts describing the environment (e.g. device, os or browser). */
  private Contexts contexts = new Contexts();
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
   * Custom tags for this event.
   *
   * <p>A map or list of tags for this event. Each tag must be less than 200 characters.
   */
  private Map<String, String> tags;
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
    this.eventId = eventId;
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
    this(new SentryId(), DateUtils.getCurrentDateTimeOrNull());
  }

  @TestOnly
  public SentryEvent(final Date timestamp) {
    this(new SentryId(), timestamp);
  }

  public SentryId getEventId() {
    return eventId;
  }

  public void setEventId(SentryId eventId) {
    this.eventId = eventId;
  }

  @SuppressWarnings("JdkObsolete")
  public Date getTimestamp() {
    return (Date) timestamp.clone();
  }

  /**
   * Returns the captured Throwable or null
   *
   * @return the Throwable or null
   */
  public @Nullable Throwable getThrowable() {
    return throwable;
  }

  /**
   * Sets the Throwable
   *
   * @param throwable the Throwable or null
   */
  public void setThrowable(final @Nullable Throwable throwable) {
    this.throwable = throwable;
  }

  public Message getMessage() {
    return message;
  }

  public void setMessage(Message message) {
    this.message = message;
  }

  public String getServerName() {
    return serverName;
  }

  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getRelease() {
    return release;
  }

  public void setRelease(String release) {
    this.release = release;
  }

  public String getDist() {
    return dist;
  }

  public void setDist(String dist) {
    this.dist = dist;
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

  public String getTransaction() {
    return transaction;
  }

  public void setTransaction(String transaction) {
    this.transaction = transaction;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Request getRequest() {
    return request;
  }

  public void setRequest(Request request) {
    this.request = request;
  }

  public SdkVersion getSdk() {
    return sdk;
  }

  public void setSdk(SdkVersion sdk) {
    this.sdk = sdk;
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

  Map<String, String> getTags() {
    return tags;
  }

  public void setTags(Map<String, String> tags) {
    this.tags = tags;
  }

  public void removeTag(@NotNull String key) {
    if (tags != null) {
      tags.remove(key);
    }
  }

  public @Nullable String getTag(final @NotNull String key) {
    if (tags != null) {
      return tags.get(key);
    }
    return null;
  }

  public void setTag(String key, String value) {
    if (tags == null) {
      tags = new HashMap<>();
    }
    tags.put(key, value);
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

  public Contexts getContexts() {
    return contexts;
  }

  public void setContexts(Contexts contexts) {
    this.contexts = contexts;
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
   * Returns true if this event has any sort of excetion
   *
   * @return true if errored or false otherwise
   */
  public boolean isErrored() {
    return exception != null && !exception.getValues().isEmpty();
  }
}
