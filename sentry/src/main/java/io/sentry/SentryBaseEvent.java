package io.sentry;

import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item sent to Sentry in the envelope. Can be either {@link SentryEvent} or the Performance
 * transaction.
 */
public abstract class SentryBaseEvent {
  public static final String DEFAULT_PLATFORM = "java";
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
  private @Nullable SentryId eventId;
  /** Contexts describing the environment (e.g. device, os or browser). */
  private final @NotNull Contexts contexts = new Contexts();
  /** Information about the Sentry SDK that generated this event. */
  private @Nullable SdkVersion sdk;
  /** Information about a web request that occurred during the event. */
  private @Nullable Request request;
  /**
   * Custom tags for this event.
   *
   * <p>A map or list of tags for this event. Each tag must be less than 200 characters.
   */
  private Map<String, String> tags;

  /**
   * The release version of the application.
   *
   * <p>**Release versions must be unique across all projects in your organization.** This value can
   * be the git SHA for the given project, or a product identifier with a semantic version.
   */
  private String release;

  /**
   * The environment name, such as `production` or `staging`.
   *
   * <p>```json { "environment": "production" } ```
   */
  private String environment;

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
  private @Nullable String platform;

  /** Information about the user who triggered this event. */
  private @Nullable User user;

  /** The captured Throwable */
  protected transient @Nullable Throwable throwable;

  /**
   * Server or device name the event was generated on.
   *
   * <p>This is supposed to be a hostname.
   */
  private String serverName;

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

  /** List of breadcrumbs recorded before this event. */
  private List<Breadcrumb> breadcrumbs;

  /**
   * Arbitrary extra information set by the user.
   *
   * <p>```json { "extra": { "my_key": 1, "some_other_value": "foo bar" } }```
   */
  private Map<String, Object> extra;

  protected SentryBaseEvent(final @NotNull SentryId eventId) {
    this.eventId = eventId;
  }

  protected SentryBaseEvent() {
    this(new SentryId());
  }

  public @Nullable SentryId getEventId() {
    return eventId;
  }

  public void setEventId(@Nullable SentryId eventId) {
    this.eventId = eventId;
  }

  public @NotNull Contexts getContexts() {
    return contexts;
  }

  public @Nullable SdkVersion getSdk() {
    return sdk;
  }

  public void setSdk(final @Nullable SdkVersion sdk) {
    this.sdk = sdk;
  }

  public @Nullable Request getRequest() {
    return request;
  }

  public void setRequest(final @Nullable Request request) {
    this.request = request;
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
   * Returns the captured Throwable or null. If a throwable is wrapped in {@link
   * ExceptionMechanismException}, returns unwrapped throwable.
   *
   * @return the Throwable or null
   */
  public @Nullable Throwable getOriginThrowable() {
    final Throwable ex = throwable;
    if (ex instanceof ExceptionMechanismException) {
      return ((ExceptionMechanismException) ex).getThrowable();
    } else {
      return ex;
    }
  }

  /**
   * Sets the Throwable
   *
   * @param throwable the Throwable or null
   */
  public void setThrowable(final @Nullable Throwable throwable) {
    this.throwable = throwable;
  }

  @ApiStatus.Internal
  public Map<String, String> getTags() {
    return tags;
  }

  public void addTags(Map<String, String> tags) {
    if (tags != null) {
      if (this.tags == null) {
        this.tags = new HashMap<>();
      }
      for (final Map.Entry<String, String> entry : tags.entrySet()) {
        this.tags.put(entry.getKey(), entry.getValue());
      }
    }
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

  public void addTag(String key, String value) {
    if (tags == null) {
      tags = new HashMap<>();
    }
    tags.put(key, value);
  }

  public String getRelease() {
    return release;
  }

  public void setRelease(String release) {
    this.release = release;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public @Nullable String getPlatform() {
    return platform;
  }

  public void setPlatform(final @Nullable String platform) {
    this.platform = platform;
  }

  public String getServerName() {
    return serverName;
  }

  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  public String getDist() {
    return dist;
  }

  public void setDist(String dist) {
    this.dist = dist;
  }

  public @Nullable User getUser() {
    return user;
  }

  public void setUser(final @Nullable User user) {
    this.user = user;
  }

  public List<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  public void addBreadcrumbs(List<Breadcrumb> breadcrumbs) {
    if (breadcrumbs != null) {
      if (this.breadcrumbs == null) {
        this.breadcrumbs = new ArrayList<>();
      }
      this.breadcrumbs.addAll(breadcrumbs);
    }
  }

  public void addBreadcrumb(Breadcrumb breadcrumb) {
    if (breadcrumbs == null) {
      breadcrumbs = new ArrayList<>();
    }
    breadcrumbs.add(breadcrumb);
  }

  Map<String, Object> getExtras() {
    return extra;
  }

  public void addExtras(Map<String, Object> extra) {
    if (extra != null) {
      if (this.extra == null) {
        this.extra = new HashMap<>();
      }
      for (final Map.Entry<String, Object> entry : extra.entrySet()) {
        this.extra.put(entry.getKey(), entry.getValue());
      }
    }
  }

  public void addExtra(String key, Object value) {
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

  public void addBreadcrumb(final @Nullable String message) {
    this.addBreadcrumb(new Breadcrumb(message));
  }
}
