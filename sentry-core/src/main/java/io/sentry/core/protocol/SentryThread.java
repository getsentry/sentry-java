package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/** Describes a thread in the Sentry protocol. */
public final class SentryThread implements IUnknownPropertiesConsumer {
  private Long id;
  private Integer priority;
  private String name;
  private String state;
  private Boolean crashed;
  private Boolean current;
  private Boolean daemon;
  private SentryStackTrace stacktrace;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  /**
   * Gets the Id of the thread.
   *
   * @return the thread id.
   */
  public Long getId() {
    return id;
  }

  /**
   * Sets the Id of the thread.
   *
   * @param id the thread id.
   */
  public void setId(Long id) {
    this.id = id;
  }

  /**
   * Gets the name of the thread.
   *
   * @return the name of the thread.
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the thread.
   *
   * @param name the name of the thread.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets whether the crash happened on this thread.
   *
   * @return whether it was the crashed thread.
   */
  public Boolean isCrashed() {
    return crashed;
  }

  /**
   * Sets whether the crash happened on this thread.
   *
   * @param crashed whether it was the crashed thread.
   */
  public void setCrashed(Boolean crashed) {
    this.crashed = crashed;
  }

  /**
   * Get an optional flag to indicate that the thread was in the foreground.
   *
   * @return whether the thread was in the foreground.
   */
  public Boolean isCurrent() {
    return current;
  }

  /**
   * Sets an optional flag to indicate that the thread was in the foreground.
   *
   * @param current whether the thread was in the foreground.
   */
  public void setCurrent(Boolean current) {
    this.current = current;
  }

  /**
   * Gets the stacktrace of the thread.
   *
   * @return the thread stacktrace.
   */
  public SentryStackTrace getStacktrace() {
    return stacktrace;
  }

  /**
   * Sets the stacktrace of the thread.
   *
   * @param stacktrace the thread stacktrace.
   */
  public void setStacktrace(SentryStackTrace stacktrace) {
    this.stacktrace = stacktrace;
  }

  /**
   * Gets the priority of the thread.
   *
   * @return the thread priority.
   */
  public Integer getPriority() {
    return priority;
  }

  /**
   * Sets the priority of the thread.
   *
   * @param priority of the thread.
   */
  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  /**
   * Gets if this thread is a daemon thread.
   *
   * @return if this is a daemon thread.
   */
  public Boolean isDaemon() {
    return daemon;
  }

  /**
   * Sets if this is a daemon thread.
   *
   * @param daemon true if the thread is daemon thread. Otherwise false.
   */
  public void setDaemon(Boolean daemon) {
    this.daemon = daemon;
  }

  /**
   * Gets the state of the thread.
   *
   * @return the state of the thread.
   */
  public String getState() {
    return state;
  }

  /**
   * Sets the state of the thread.
   *
   * @param state the state of the thread.
   */
  public void setState(String state) {
    this.state = state;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
