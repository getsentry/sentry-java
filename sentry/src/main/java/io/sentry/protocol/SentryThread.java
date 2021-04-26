package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A process thread of an event.
 *
 * <p>The Threads Interface specifies threads that were running at the time an event happened. These
 * threads can also contain stack traces.
 *
 * <p>An event may contain one or more threads in an attribute named `threads`.
 *
 * <p>The following example illustrates the threads part of the event payload and omits other
 * attributes for simplicity.
 *
 * <p>```json { "threads": { "values": [ { "id": "0", "name": "main", "crashed": true, "stacktrace":
 * {} } ] } } ```
 */
public final class SentryThread implements IUnknownPropertiesConsumer {
  private @Nullable Long id;
  private @Nullable Integer priority;
  private @Nullable String name;
  private @Nullable String state;
  private @Nullable Boolean crashed;
  private @Nullable Boolean current;
  private @Nullable Boolean daemon;
  private @Nullable SentryStackTrace stacktrace;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  /**
   * Gets the Id of the thread.
   *
   * @return the thread id.
   */
  public @Nullable Long getId() {
    return id;
  }

  /**
   * Sets the Id of the thread.
   *
   * @param id the thread id.
   */
  public void setId(final @Nullable Long id) {
    this.id = id;
  }

  /**
   * Gets the name of the thread.
   *
   * @return the name of the thread.
   */
  public @Nullable String getName() {
    return name;
  }

  /**
   * Sets the name of the thread.
   *
   * @param name the name of the thread.
   */
  public void setName(final @Nullable String name) {
    this.name = name;
  }

  /**
   * Gets whether the crash happened on this thread.
   *
   * @return whether it was the crashed thread.
   */
  public @Nullable Boolean isCrashed() {
    return crashed;
  }

  /**
   * Sets whether the crash happened on this thread.
   *
   * @param crashed whether it was the crashed thread.
   */
  public void setCrashed(final @Nullable Boolean crashed) {
    this.crashed = crashed;
  }

  /**
   * Get an optional flag to indicate that the thread was in the foreground.
   *
   * @return whether the thread was in the foreground.
   */
  public @Nullable Boolean isCurrent() {
    return current;
  }

  /**
   * Sets an optional flag to indicate that the thread was in the foreground.
   *
   * @param current whether the thread was in the foreground.
   */
  public void setCurrent(final @Nullable Boolean current) {
    this.current = current;
  }

  /**
   * Gets the stacktrace of the thread.
   *
   * @return the thread stacktrace.
   */
  public @Nullable SentryStackTrace getStacktrace() {
    return stacktrace;
  }

  /**
   * Sets the stacktrace of the thread.
   *
   * @param stacktrace the thread stacktrace.
   */
  public void setStacktrace(final @Nullable SentryStackTrace stacktrace) {
    this.stacktrace = stacktrace;
  }

  /**
   * Gets the priority of the thread.
   *
   * @return the thread priority.
   */
  public @Nullable Integer getPriority() {
    return priority;
  }

  /**
   * Sets the priority of the thread.
   *
   * @param priority of the thread.
   */
  public void setPriority(final @Nullable Integer priority) {
    this.priority = priority;
  }

  /**
   * Gets if this thread is a daemon thread.
   *
   * @return if this is a daemon thread.
   */
  public @Nullable Boolean isDaemon() {
    return daemon;
  }

  /**
   * Sets if this is a daemon thread.
   *
   * @param daemon true if the thread is daemon thread. Otherwise false.
   */
  public void setDaemon(final @Nullable Boolean daemon) {
    this.daemon = daemon;
  }

  /**
   * Gets the state of the thread.
   *
   * @return the state of the thread.
   */
  public @Nullable String getState() {
    return state;
  }

  /**
   * Sets the state of the thread.
   *
   * @param state the state of the thread.
   */
  public void setState(final @Nullable String state) {
    this.state = state;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
