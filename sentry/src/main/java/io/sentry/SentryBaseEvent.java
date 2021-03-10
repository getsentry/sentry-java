package io.sentry;

import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item sent to Sentry in the envelope. Can be either {@link SentryEvent} or the Performance
 * transaction.
 */
public abstract class SentryBaseEvent {
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

  /** The captured Throwable */
  protected transient @Nullable Throwable throwable;

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
}
