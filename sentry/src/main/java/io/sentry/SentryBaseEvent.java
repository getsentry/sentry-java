package io.sentry;

import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.CollectionUtils;
import java.io.IOException;
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
  private @Nullable Map<String, String> tags;

  /**
   * The release version of the application.
   *
   * <p>**Release versions must be unique across all projects in your organization.** This value can
   * be the git SHA for the given project, or a product identifier with a semantic version.
   */
  private @Nullable String release;

  /**
   * The environment name, such as `production` or `staging`.
   *
   * <p>```json { "environment": "production" } ```
   */
  private @Nullable String environment;

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
  private @Nullable String serverName;

  /**
   * Program's distribution identifier.
   *
   * <p>The distribution of the application.
   *
   * <p>Distributions are used to disambiguate build or deployment variants of the same release of
   * an application. For example, the dist can be the build number of an XCode build or the version
   * code of an Android build.
   */
  private @Nullable String dist;

  /** List of breadcrumbs recorded before this event. */
  private @Nullable List<Breadcrumb> breadcrumbs;

  /** Meta data for event processing and debugging. */
  private @Nullable DebugMeta debugMeta;

  /**
   * Arbitrary extra information set by the user.
   *
   * <p>```json { "extra": { "my_key": 1, "some_other_value": "foo bar" } }```
   */
  private @Nullable Map<String, Object> extra;

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
   * Returns the captured Throwable or null. If a throwable is wrapped in {@link
   * ExceptionMechanismException}, returns unwrapped throwable.
   *
   * @return the Throwable or null
   */
  public @Nullable Throwable getThrowable() {
    final Throwable ex = throwable;
    if (ex instanceof ExceptionMechanismException) {
      return ((ExceptionMechanismException) ex).getThrowable();
    } else {
      return ex;
    }
  }

  /**
   * Returns the captured Throwable or null. It may be wrapped in a {@link
   * ExceptionMechanismException}.
   *
   * @return the Throwable or null
   */
  @ApiStatus.Internal
  @Nullable
  public Throwable getThrowableMechanism() {
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

  @ApiStatus.Internal
  public @Nullable Map<String, String> getTags() {
    return tags;
  }

  public void setTags(@Nullable Map<String, String> tags) {
    this.tags = CollectionUtils.newHashMap(tags);
  }

  public void removeTag(@Nullable String key) {
    if (tags != null && key != null) {
      tags.remove(key);
    }
  }

  public @Nullable String getTag(final @Nullable String key) {
    if (tags != null && key != null) {
      return tags.get(key);
    }
    return null;
  }

  public void setTag(final @Nullable String key, final @Nullable String value) {
    if (tags == null) {
      tags = new HashMap<>();
    }
    if (key == null) {
      return;
    }
    if (value == null) {
      removeTag(key);
    } else {
      tags.put(key, value);
    }
  }

  public @Nullable String getRelease() {
    return release;
  }

  public void setRelease(final @Nullable String release) {
    this.release = release;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public void setEnvironment(final @Nullable String environment) {
    this.environment = environment;
  }

  public @Nullable String getPlatform() {
    return platform;
  }

  public void setPlatform(final @Nullable String platform) {
    this.platform = platform;
  }

  public @Nullable String getServerName() {
    return serverName;
  }

  public void setServerName(final @Nullable String serverName) {
    this.serverName = serverName;
  }

  public @Nullable String getDist() {
    return dist;
  }

  public void setDist(final @Nullable String dist) {
    this.dist = dist;
  }

  public @Nullable User getUser() {
    return user;
  }

  public void setUser(final @Nullable User user) {
    this.user = user;
  }

  public @Nullable List<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  public void setBreadcrumbs(final @Nullable List<Breadcrumb> breadcrumbs) {
    this.breadcrumbs = CollectionUtils.newArrayList(breadcrumbs);
  }

  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    if (breadcrumbs == null) {
      breadcrumbs = new ArrayList<>();
    }
    breadcrumbs.add(breadcrumb);
  }

  public @Nullable DebugMeta getDebugMeta() {
    return debugMeta;
  }

  public void setDebugMeta(final @Nullable DebugMeta debugMeta) {
    this.debugMeta = debugMeta;
  }

  @Nullable
  public Map<String, Object> getExtras() {
    return extra;
  }

  public void setExtras(final @Nullable Map<String, Object> extra) {
    this.extra = CollectionUtils.newHashMap(extra);
  }

  public void setExtra(final @Nullable String key, final @Nullable Object value) {
    if (extra == null) {
      extra = new HashMap<>();
    }
    if (key == null) {
      return;
    }
    if (value == null) {
      removeExtra(key);
    } else {
      extra.put(key, value);
    }
  }

  public void removeExtra(final @Nullable String key) {
    if (extra != null && key != null) {
      extra.remove(key);
    }
  }

  public @Nullable Object getExtra(final @Nullable String key) {
    if (extra != null && key != null) {
      return extra.get(key);
    }
    return null;
  }

  public void addBreadcrumb(final @Nullable String message) {
    this.addBreadcrumb(new Breadcrumb(message));
  }

  // region json

  public static final class JsonKeys {
    public static final String EVENT_ID = "event_id";
    public static final String CONTEXTS = "contexts";
    public static final String SDK = "sdk";
    public static final String REQUEST = "request";
    public static final String TAGS = "tags";
    public static final String RELEASE = "release";
    public static final String ENVIRONMENT = "environment";
    public static final String PLATFORM = "platform";
    public static final String USER = "user";
    public static final String SERVER_NAME = "server_name";
    public static final String DIST = "dist";
    public static final String BREADCRUMBS = "breadcrumbs";

    public static final String DEBUG_META = "debug_meta";

    public static final String EXTRA = "extra";
  }

  public static final class Serializer {
    public void serialize(
        @NotNull SentryBaseEvent baseEvent, @NotNull ObjectWriter writer, @NotNull ILogger logger)
        throws IOException {
      if (baseEvent.eventId != null) {
        writer.name(JsonKeys.EVENT_ID).value(logger, baseEvent.eventId);
      }
      writer.name(JsonKeys.CONTEXTS).value(logger, baseEvent.contexts);
      if (baseEvent.sdk != null) {
        writer.name(JsonKeys.SDK).value(logger, baseEvent.sdk);
      }
      if (baseEvent.request != null) {
        writer.name(JsonKeys.REQUEST).value(logger, baseEvent.request);
      }
      if (baseEvent.tags != null && !baseEvent.tags.isEmpty()) {
        writer.name(JsonKeys.TAGS).value(logger, baseEvent.tags);
      }
      if (baseEvent.release != null) {
        writer.name(JsonKeys.RELEASE).value(baseEvent.release);
      }
      if (baseEvent.environment != null) {
        writer.name(JsonKeys.ENVIRONMENT).value(baseEvent.environment);
      }
      if (baseEvent.platform != null) {
        writer.name(JsonKeys.PLATFORM).value(baseEvent.platform);
      }
      if (baseEvent.user != null) {
        writer.name(JsonKeys.USER).value(logger, baseEvent.user);
      }
      if (baseEvent.serverName != null) {
        writer.name(JsonKeys.SERVER_NAME).value(baseEvent.serverName);
      }
      if (baseEvent.dist != null) {
        writer.name(JsonKeys.DIST).value(baseEvent.dist);
      }
      if (baseEvent.breadcrumbs != null && !baseEvent.breadcrumbs.isEmpty()) {
        writer.name(JsonKeys.BREADCRUMBS).value(logger, baseEvent.breadcrumbs);
      }
      if (baseEvent.debugMeta != null) {
        writer.name(JsonKeys.DEBUG_META).value(logger, baseEvent.debugMeta);
      }
      if (baseEvent.extra != null && !baseEvent.extra.isEmpty()) {
        writer.name(JsonKeys.EXTRA).value(logger, baseEvent.extra);
      }
    }
  }

  public static final class Deserializer {
    @SuppressWarnings("unchecked")
    public boolean deserializeValue(
        @NotNull SentryBaseEvent baseEvent,
        @NotNull String nextName,
        @NotNull ObjectReader reader,
        @NotNull ILogger logger)
        throws Exception {
      switch (nextName) {
        case JsonKeys.EVENT_ID:
          baseEvent.eventId = reader.nextOrNull(logger, new SentryId.Deserializer());
          return true;
        case JsonKeys.CONTEXTS:
          Contexts deserializedContexts = new Contexts.Deserializer().deserialize(reader, logger);
          baseEvent.contexts.putAll(deserializedContexts);
          return true;
        case JsonKeys.SDK:
          baseEvent.sdk = reader.nextOrNull(logger, new SdkVersion.Deserializer());
          return true;
        case JsonKeys.REQUEST:
          baseEvent.request = reader.nextOrNull(logger, new Request.Deserializer());
          return true;
        case JsonKeys.TAGS:
          Map<String, String> deserializedTags = (Map<String, String>) reader.nextObjectOrNull();
          baseEvent.tags = CollectionUtils.newConcurrentHashMap(deserializedTags);
          return true;
        case JsonKeys.RELEASE:
          baseEvent.release = reader.nextStringOrNull();
          return true;
        case JsonKeys.ENVIRONMENT:
          baseEvent.environment = reader.nextStringOrNull();
          return true;
        case JsonKeys.PLATFORM:
          baseEvent.platform = reader.nextStringOrNull();
          return true;
        case JsonKeys.USER:
          baseEvent.user = reader.nextOrNull(logger, new User.Deserializer());
          return true;
        case JsonKeys.SERVER_NAME:
          baseEvent.serverName = reader.nextStringOrNull();
          return true;
        case JsonKeys.DIST:
          baseEvent.dist = reader.nextStringOrNull();
          return true;
        case JsonKeys.BREADCRUMBS:
          baseEvent.breadcrumbs = reader.nextListOrNull(logger, new Breadcrumb.Deserializer());
          return true;
        case JsonKeys.DEBUG_META:
          baseEvent.debugMeta = reader.nextOrNull(logger, new DebugMeta.Deserializer());
          return true;
        case JsonKeys.EXTRA:
          Map<String, Object> deserializedExtra = (Map<String, Object>) reader.nextObjectOrNull();
          baseEvent.extra = CollectionUtils.newConcurrentHashMap(deserializedExtra);
          return true;
      }
      return false;
    }
  }

  // endregion
}
